import {createStore} from "vuex";
import {createRouter, createWebHistory} from "vue-router";
import VueGtag from "vue-gtag";
import {loadLocaleMessages, setI18nLanguage, setupI18n} from "../translations/i18n";
import moment from "moment-timezone";
import "moment/dist/locale/de"
import "moment/dist/locale/es"
import "moment/dist/locale/fr"
import "moment/dist/locale/hi"
import "moment/dist/locale/it"
import "moment/dist/locale/ja"
import "moment/dist/locale/ko"
import "moment/dist/locale/pl"
import "moment/dist/locale/pt"
import "moment/dist/locale/ru"
import "moment/dist/locale/zh-cn"
import {extendMoment} from "moment-range";
import VueSidebarMenu from "vue-sidebar-menu";
import {
    ArcElement,
    BarController,
    BarElement,
    CategoryScale,
    Chart,
    DoughnutController,
    Filler,
    Legend,
    LinearScale,
    LineController,
    LineElement,
    PointElement,
    Tooltip,
} from "chart.js";
import Vue3Tour from "vue3-tour"
import VueVirtualScroller from "vue-virtual-scroller";
import {createPinia} from "pinia";

import Toast from "./toast";
import filters from "./filters";
import ElementPlus from "element-plus";
import createUnsavedChanged from "./unsavedChange";
import createEventsRouter from "./eventsRouter";
import "./global"


import LeftMenuLink from "../components/LeftMenuLink.vue";
import RouterMd from "../components/utils/RouterMd.vue";
import Utils from "./utils";



export default async (app, routes, stores, translations, additionalTranslations = {}) => {
    // charts
    Chart.register(
        CategoryScale,
        LinearScale,
        BarElement,
        BarController,
        LineElement,
        LineController,
        PointElement,
        Filler,
        ArcElement,
        DoughnutController,
        Tooltip,
        Legend,
        CategoryScale,
        LinearScale
    );

    // store
    let store = createStore(stores);
    app.use(store);

    let pinia = createPinia();
    app.use(pinia);

    // router
    let router = createRouter({
        history: createWebHistory(window.KESTRA_UI_PATH),
        routes
    });

    /**
     * Manage docId initialization for Contextual docs
     */
    router.beforeEach((to, from, next) => {
        // set the docId from the path
        // so it has a default
        const pathArray = to.path.split("/");
        const docId = pathArray[pathArray.length-1];
        store.commit("doc/setDocId", docId);

        // propagate showDocId query param
        // to the next page to facilitate docs binding
        if(to.query["showDocId"] === undefined && from.query["showDocId"] !== undefined){
            next({path: to.path, query: {...to.query, showDocId: from.query["showDocId"]}})
        }else{
            next()
        }
    })

    router.afterEach((to) => {
        window.dispatchEvent(new CustomEvent("KestraRouterAfterEach", to))
    })

    // avoid loading router in storybook
    // as it conflicts with storybook's
    if(routes.length){
        app.use(router)
    }

    // Google Analytics
    if (window.KESTRA_GOOGLE_ANALYTICS !== null) {
        app.use(
            VueGtag,
            {
                config: {id: window.KESTRA_GOOGLE_ANALYTICS}
            },
            router
        );
    }



    // l18n
    let locale = Utils.getLang();

    let i18n = setupI18n({
        locale: "en",
        messages: translations,
        allowComposition: true,
        legacy: false,
        warnHtmlMessage: false,
    });

    if(locale !== "en"){
        await loadLocaleMessages(i18n, locale, additionalTranslations);
        await setI18nLanguage(i18n, locale);
    }
    app.use(i18n);
    store.$i18n = i18n.global;

    // moment
    moment.locale(locale);
    app.config.globalProperties.$moment = extendMoment(moment);

    // others plugins
    app.use(VueSidebarMenu);
    app.use(Toast)
    app.provide("Toast", Toast)
    app.use(Vue3Tour)
    app.use(VueVirtualScroller)

    // Passing toast to VUEX store to be used in modules
    store.$toast = app.config.globalProperties.$toast;

    // filters
    app.config.globalProperties.$filters = filters;

    // element-plus
    app.use(ElementPlus)

    // navigation guard
    createUnsavedChanged(app, store, router);
    createEventsRouter(app, store, router);

    app.component("LeftMenuLink", LeftMenuLink);
    app.component("RouterMd", RouterMd);
    const components = {
        ...(import.meta.glob("../../node_modules/@nuxtjs/mdc/dist/runtime/components/prose/*.vue", {eager: true})),
        ...(import.meta.glob("../../node_modules/@kestra-io/ui-libs/src/components/content/*.vue", {eager: true})),
        ...(import.meta.glob("../components/content/*.vue", {eager: true})),
    };
    const componentsByName = Object.entries(components)
        .map(([path, component]) => [path.replace(/^.*\/(.*)\.vue$/, "$1"), component.default]);
    const componentsNames = componentsByName.map(([name]) => name);
    componentsByName.filter(([name], index) => componentsNames.lastIndexOf(name) === index)
        .forEach(([name, component]) => app.component(name, component));

    app.config.globalProperties.append = (path, pathToAppend) => path + (path.endsWith("/") ? "" : "/") + pathToAppend

    return {store, router};
}
