import {nextTick} from "vue";
import _isEqual from "lodash/isEqual";
import {useApiStore} from "../stores/api";

export const pageFromRoute = (route) => {
    return {
        origin: window.location.origin,
        path: route.path,
        params: Object.keys(route.params)
            .map((key) => ({key: key, value: route.params[key]})),
        queries: Object.keys(route.query)
            .map((key) => {
                return {key: key, values: (route.query[key] instanceof Array ? route.query[key] : [route.query[key]])}
            }),
        name: route.name,
        hash: route.hash !== "" ? route.hash : undefined,
    }
}

export default (_app, router) => {
    const apiStore = useApiStore();
    router.afterEach((to, from) => {
        nextTick().then(() => {
            if (_isEqual(from, to)) {
                return;
            }
            apiStore.events({
                type: "PAGE",
                page: pageFromRoute(to)
            });
        });
    });
}
