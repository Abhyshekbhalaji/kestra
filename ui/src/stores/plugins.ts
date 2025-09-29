import {defineStore} from "pinia"
import {trackPluginDocumentationView} from "../utils/tabTracking";;
import {apiUrlWithoutTenants} from "override/utils/route";
import semver from "semver";
import {useApiStore} from "./api";
import {Schemas} from "../components/no-code/utils/types";
import InitialFlowSchema from "./flow-schema.json"
import {toRaw} from "vue";
import {isEntryAPluginElementPredicate} from "@kestra-io/ui-libs";

export interface PluginComponent {
    icon?: string;
    cls?: string;
    deprecated?: boolean;
    version?: string;
    description?: string;
    properties?: Record<string, any>;
    schema: Schemas;
    markdown?: string;
}

export interface Plugin {
    tasks: PluginComponent[];
    triggers: PluginComponent[];
    conditions: PluginComponent[];
    controllers: PluginComponent[];
    storages: PluginComponent[];
    taskRunners: PluginComponent[];
    charts: PluginComponent[];
    dataFilters: PluginComponent[];
    dataFiltersKPI: PluginComponent[];
    aliases: PluginComponent[];
    logExporters: PluginComponent[];
    apps: PluginComponent[];
    appBlocks: PluginComponent[];
    additionalPlugins: PluginComponent[];
}

interface State {
    plugin?: PluginComponent;
    versions?: string[];
    pluginAllProps?: any;
    deprecatedTypes?: string[];
    plugins?: Plugin[];
    icons?: Record<string, string>;
    pluginsDocumentation: Record<string, PluginComponent>;
    editorPlugin?: (PluginComponent & {cls: string});
    inputSchema?: any;
    inputsType?: any;
    schemaType?: Record<string, any>;
    currentlyLoading?: {
        type?: string;
        version?: string;
    };
    forceIncludeProperties?: string[];
    _iconsPromise: Promise<Record<string, string>> | undefined;
}

interface LoadOptions {
    cls: string;
    version?: string;
    all?: boolean;
    commit?: boolean;
    hash?: number;
}

interface JsonSchemaDef {
    $ref?: string,
    allOf?: JsonSchemaDef[],
    type?: string,
    properties?: Record<string, any>,
}

export function removeRefPrefix(ref?: string): string {
    return ref?.replace(/^#\/definitions\//, "") ?? "";
}

export const usePluginsStore = defineStore("plugins", {
    state: (): State => ({
        plugin: undefined,
        versions: undefined,
        pluginAllProps: undefined,
        plugins: undefined,
        icons: undefined,
        pluginsDocumentation: {},
        editorPlugin: undefined,
        inputSchema: undefined,
        inputsType: undefined,
        schemaType: undefined,
        _iconsPromise: undefined
    }),
    getters: {
        flowSchema(state): {
            definitions: any,
            $ref: string,
        } {
            return state.schemaType?.flow ?? InitialFlowSchema;
        },
        flowDefinitions(): Record<string, any> | undefined {
            return this.flowSchema.definitions;
        },
        flowRootSchema(): Record<string, any> | undefined {
            return this.flowDefinitions?.[removeRefPrefix(this.flowSchema.$ref)];
        },
        flowRootProperties(): Record<string, any> | undefined {
            return this.flowRootSchema?.properties;
        },
        allTypes(): string[] {
            return this.plugins?.flatMap(plugin => Object.entries(plugin))
                ?.filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                ?.flatMap(([, value]: [string, PluginComponent[]]) => value.map(({cls}) => cls!)) ?? [];
        },
        deprecatedTypes(): string[] {
            return this.plugins?.flatMap(plugin => Object.entries(plugin))
                ?.filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                ?.flatMap(([, value]: [string, PluginComponent[]]) => value.filter(({deprecated}) => deprecated === true).map(({cls}) => cls!)) ?? [];
        }
    },
    actions: {
        resolveRef(obj: JsonSchemaDef): JsonSchemaDef {
            if (obj?.$ref) {
                return this.flowDefinitions?.[removeRefPrefix(obj.$ref)];
            }
            if (obj?.allOf) {
                const def = obj.allOf.reduce((acc: any, item) => {
                    if (item.$ref) {
                        const ref = toRaw(this.flowDefinitions?.[removeRefPrefix(item.$ref)]);
                        if (ref?.type === "object" && ref?.properties) {
                            acc.properties = {
                                ...acc.properties,
                                ...ref.properties
                            };
                        }
                    }
                    if (item.type === "object" && item.properties) {
                        acc.properties = {
                            ...acc.properties,
                            ...item.properties
                        };
                    }
                    return acc;
                }, {});
                return def
            }
            return obj;
        },

        async filteredPlugins(excludedElements: string[]) {
            if (this.plugins === undefined) {
                this.plugins = await this.listWithSubgroup({includeDeprecated: false});
            }

            return this.plugins.map(p => ({
                ...p,
                ...Object.fromEntries(excludedElements.map(e => [e, undefined]))
            })).filter(p => Object.entries(p)
                    .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                    .some(([, value]: [string, PluginComponent[]]) => value.length !== 0))
        },

        async list() {
            const response = await this.$http.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins`);
            this.plugins = response.data;
            return response.data;
        },

        async listWithSubgroup(options: Record<string, any>) {
            const response = await this.$http.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins/groups/subgroups`, {
                params: options
            });
            this.plugins = response.data;
            return response.data;
        },

        async load(options: LoadOptions) {
            if (options.cls === undefined) {
                throw new Error("missing required cls");
            }

            const id = options.version ? `${options.cls}/${options.version}` : options.cls;
            const cachedPluginDoc = this.pluginsDocumentation[options.hash ? options.hash + id : id];
            if (!options.all && cachedPluginDoc) {
                this.plugin = cachedPluginDoc;
                return cachedPluginDoc;
            }

            const baseUrl = options.version ?
                `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions/${options.version}` :
                `${apiUrlWithoutTenants()}/plugins/${options.cls}`;

            const url = options.hash ? `${baseUrl}?hash=${options.hash}` : baseUrl;

            const response = await this.$http.get<PluginComponent>(url);

            if (options.commit !== false) {
                if (options.all === true) {
                    this.pluginAllProps = response.data;
                } else {
                    this.plugin = response.data;
                }
            }

            if (!options.all) {
                this.pluginsDocumentation = {
                    ...this.pluginsDocumentation,
                    [options.hash ? options.hash+id : id]: response.data
                };
            }

            return response.data;
        },

        async loadVersions(options: {cls: string; commit?: boolean}): Promise<{type: string, versions: string[]}> {
            const response = await this.$http.get(
                `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions`
            );
            if (options.commit !== false) {
                this.versions = response.data.versions;
            }

            return response.data;
        },

        fetchIcons() {
            if (this.icons) {
                return Promise.resolve(this.icons);
            }

            if (this._iconsPromise) {
                return this._iconsPromise;
            }

            const apiStore = useApiStore();

            const apiPromise = apiStore.pluginIcons().then(response => {
                // to avoid unnecessary dom updates and calculations in the reactivity rendering of Vue,
                // we do all our updates to a temporary object, then commit the changes all at once
                const tempIcons = toRaw(this.icons) ?? {};
                for (const [key, plugin] of Object.entries(response.data)) {
                    if (tempIcons && tempIcons[key] === undefined) {
                        tempIcons[key] = plugin as string;
                    }
                }
                this.icons = tempIcons;
            });

            const iconsPromise =
                this.$http.get(`${apiUrlWithoutTenants()}/plugins/icons`, {}).then(response => {
                    const icons = response.data ?? {};
                    this.icons = this.icons ? {
                        ...icons,
                        ...this.icons
                    } : icons;
                });

            this._iconsPromise = Promise.all([apiPromise, iconsPromise]).then(() => {
                return this.icons ?? {};
            })

            return this._iconsPromise;
        },

        groupIcons() {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/icons/groups`, {})
            .then(response => {
                return response.data;
            });
        },

        loadInputsType() {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/inputs`, {}).then(response => {
                this.inputsType = response.data;

                return response.data;
            });
        },
        loadInputSchema(options: {type: string}) {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/inputs/${options.type}`, {}).then(response => {
                this.inputSchema = response.data;

                return response.data;
            });
        },

        loadSchemaType(options: {type: string} = {type: "flow"}) {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/schemas/${options.type}`, {}).then(response => {
                this.schemaType = this.schemaType || {};
                this.schemaType[options.type] = response.data;
                return response.data;
            });
        },


        async updateDocumentation(pluginElement?: ({type: string, version?: string, forceRefresh?: boolean} & Record<string, any>) | undefined) {
            if (!pluginElement?.type || !this.allTypes.includes(pluginElement.type)) {
                this.editorPlugin = undefined;
                this.currentlyLoading = undefined;
                return;
            }

            const {type, version, forceRefresh = false} = pluginElement;

            // Avoid rerunning the same request twice in a row
            if (this.currentlyLoading?.type === type &&
                this.currentlyLoading?.version === version &&
                !forceRefresh) {
                return
            }

            if (!forceRefresh &&
                this.editorPlugin?.cls === type &&
                this.editorPlugin?.version === version) {
                return;
            }

            let payload: LoadOptions = {cls: type, hash: pluginElement.hash};

            if (version !== undefined) {
                // Check if the version is valid to avoid error
                // when loading plugin
                if (semver.valid(version) !== null ||
                    "latest" === version.toString().toLowerCase() ||
                    "oldest" === version.toString().toLowerCase()
                ) {
                    payload = {
                        ...payload,
                        version
                    };
                }
            }

            this.currentlyLoading = {
                type,
                version,
            };

            this.load(payload).then((plugin) => {
                this.editorPlugin = {
                    cls: type,
                    version,
                    ...plugin,
                };

                trackPluginDocumentationView(type);

                this.forceIncludeProperties = Object.keys(pluginElement).filter(k => k !== "type" && k !== "version" && k !== "forceRefresh");
            });
        }
    },

});
