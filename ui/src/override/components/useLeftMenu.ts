import {computed} from "vue";
import {useRoute, useRouter} from "vue-router";
import {useI18n} from "vue-i18n";
import {useMiscStore} from "override/stores/misc";

import {getDashboard} from "../../components/dashboard/composables/useDashboards";

import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
import TimelineClockOutline from "vue-material-design-icons/TimelineClockOutline.vue";
import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
import BallotOutline from "vue-material-design-icons/BallotOutline.vue";
import ShieldAccountVariantOutline from "vue-material-design-icons/ShieldAccountVariantOutline.vue";
import ViewDashboardVariantOutline from "vue-material-design-icons/ViewDashboardVariantOutline.vue";
import Connection from "vue-material-design-icons/Connection.vue";
import DotsSquare from "vue-material-design-icons/DotsSquare.vue";
import FormatListGroupPlus from "vue-material-design-icons/FormatListGroupPlus.vue";
import DatabaseOutline from "vue-material-design-icons/DatabaseOutline.vue";
import ShieldKeyOutline from "vue-material-design-icons/ShieldKeyOutline.vue";
import FlaskOutline from "vue-material-design-icons/FlaskOutline.vue";

export type MenuItem = {
    href?: {
        path?: string,
        name: string,
        params?: Record<string, any>,
        query?: Record<string, any>
    },
    child?: MenuItem[],
    disabled?: boolean,
};

export function useLeftMenu() {
    const {t} = useI18n({useScope: "global"});
    const $route = useRoute();
    const $router = useRouter();
    const miscStore = useMiscStore();

    /**
     * Returns all route names that start with the given route
     * @param route
     * @returns
     */
    function routeStartWith(route: string) {
        return $router
            ?.getRoutes()
            .filter(
                (r) => typeof r.name === "string" && r.name.startsWith(route),
            )
            .map((r) => r.name);
    }

    const flatMenuItems = (items: MenuItem[]): MenuItem[] => {
        return items.flatMap(item => item.child ? [item, ...flatMenuItems(item.child)] : [item])
    }

    const menu = computed(() => {
        const generatedMenu = [
            {
                href: {
                    name: "home",
                    params: {dashboard: getDashboard($route, "id")},
                },
                title: t("dashboards.labels.plural"),
                icon: {
                    element: ViewDashboardVariantOutline,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "flows/list"},
                routes: routeStartWith("flows"),
                title: t("flows"),
                icon: {
                    element: FileTreeOutline,
                    class: "menu-icon",
                },
                exact: false,
            },
            {
                href: {name: "apps/list"},
                routes: routeStartWith("apps"),
                title: t("apps"),
                icon: {
                    element: FormatListGroupPlus,
                    class: "menu-icon",
                },
                attributes: {
                    locked: true,
                },
            },
            {
                href: {name: "templates/list"},
                routes: routeStartWith("templates"),
                title: t("templates"),
                icon: {
                    element: ContentCopy,
                    class: "menu-icon",
                },
                hidden: !miscStore.configs?.isTemplateEnabled,
            },
            {
                href: {name: "executions/list"},
                routes: routeStartWith("executions"),
                title: t("executions"),
                icon: {
                    element: TimelineClockOutline,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "logs/list"},
                routes: routeStartWith("logs"),
                title: t("logs"),
                icon: {
                    element: TimelineTextOutline,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "tests/list"},
                routes: routeStartWith("tests"),
                title: t("demos.tests.label"),
                icon: {
                    element: FlaskOutline,
                    class: "menu-icon"
                },
                attributes: {
                    locked: true,
                },
            },
            {
                href: {name: "namespaces/list"},
                routes: routeStartWith("namespaces"),
                title: t("namespaces"),
                icon: {
                    element: DotsSquare,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "kv/list"},
                routes: routeStartWith("kv"),
                title: t("kv.name"),
                icon: {
                    element: DatabaseOutline,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "secrets/list"},
                routes: routeStartWith("secrets"),
                title: t("secret.names"),
                icon: {
                    element: ShieldKeyOutline,
                    class: "menu-icon",
                },
                attributes: {
                    locked: true,
                },
            },
            {
                routes: routeStartWith("blueprints"),
                title: t("blueprints.title"),
                icon: {
                    element: BallotOutline,
                    class: "menu-icon",
                },
                child: [
                    {
                        title: t("blueprints.custom"),
                        routes: routeStartWith("blueprints/flow"),
                        attributes: {
                            locked: true,
                        },
                        href: {
                            name: "blueprints",
                            params: {kind: "flow", tab: "custom"},
                        },
                    },
                    {
                        title: t("blueprints.flows"),
                        routes: routeStartWith("blueprints/flow"),
                        href: {
                            name: "blueprints",
                            params: {kind: "flow", tab: "community"},
                        },
                    },
                    {
                        title: t("blueprints.dashboards"),
                        routes: routeStartWith("blueprints/dashboard"),
                        href: {
                            name: "blueprints",
                            params: {kind: "dashboard", tab: "community"},
                        },
                    },
                ],
            },
            {
                href: {name: "plugins/list"},
                routes: routeStartWith("plugins"),
                title: t("plugins.names"),
                icon: {
                    element: Connection,
                    class: "menu-icon",
                },
            },
            {
                title: t("administration"),
                routes: routeStartWith("admin"),
                icon: {
                    element: ShieldAccountVariantOutline,
                    class: "menu-icon",
                },
                child: [
                    {
                        href: {name: "admin/iam"},
                        routes: routeStartWith("admin/iam"),
                        title: t("iam"),
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        href: {name: "admin/auditlogs/list"},
                        routes: routeStartWith("admin/auditlogs"),
                        title: t("auditlogs"),
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        href: {name: "admin/triggers"},
                        routes: routeStartWith("admin/triggers"),
                        title: t("triggers"),
                    },
                    {
                        href: {name: "admin/instance"},
                        routes: routeStartWith("admin/instance"),
                        title: t("instance"),
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        href: {name: "admin/tenants/list"},
                        routes: routeStartWith("admin/tenants"),
                        title: t("tenant.names"),
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        href: {name: "admin/stats"},
                        routes: routeStartWith("admin/stats"),
                        title: t("system overview"),
                    },
                ],
            }
        ];

        flatMenuItems(generatedMenu).forEach(menuItem => {
            if (menuItem.href !== undefined && menuItem.href?.name === $route.name) {
                menuItem.href.query = {...$route.query, ...menuItem.href?.query};
            }
        });

        return generatedMenu;
    });

    return {
        routeStartWith,
        menu
    };
}
