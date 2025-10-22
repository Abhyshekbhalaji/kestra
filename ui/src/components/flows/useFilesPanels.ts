import {computed, h, markRaw, provide, Ref} from "vue"
import EditorWrapper, {EditorTabProps, FILES_SET_DIRTY_INJECTION_KEY, FILES_UPDATE_CONTENT_INJECTION_KEY} from "../inputs/EditorWrapper.vue";
import TypeIcon from "../utils/icons/Type.vue";
import {EditorElement, Panel, Tab, TabLive} from "../../utils/multiPanelTypes";
import {FILES_CLOSE_TAB_INJECTION_KEY, FILES_OPEN_TAB_INJECTION_KEY} from "../inputs/EditorSidebar.vue";
import {FILES_SAVE_ALL_INJECTION_KEY} from "../inputs/EditorButtonsWrapper.vue";
import {useNamespacesStore} from "../../override/stores/namespaces";

export const CODE_PREFIX = "code"

function generateUid(tab: Pick<EditorTabProps, "path">){
    if(tab.path === "Flow.yaml"){
        return CODE_PREFIX
    }
    return `${CODE_PREFIX}-${tab.path}`
}

export function getTabFromFilesTab(tab: EditorTabProps): Tab {
    return {
        uid: generateUid(tab),
        button: {
            label: tab.name,
            icon: () => h(TypeIcon, {name:tab.name}),
        },
        component: () => h(markRaw(EditorWrapper), tab)
    } satisfies Tab
}

export function getTabPropsFromFilePath(filePath: string, flow: boolean = false): EditorTabProps {
    return {
        name: filePath.split("/").pop()!,
        path: filePath,
        extension: filePath.split(".").pop()!,
        flow,
        dirty: false
    }
}

interface TabLiveWithContent extends TabLive {
    content?: string
    namespace?: string
    path?: string
}

export function useInitialFilesTabs(EDITOR_ELEMENTS: EditorElement[]){
    const codeElement = EDITOR_ELEMENTS.find(e => e.uid === CODE_PREFIX)!
    codeElement.deserialize = (value: string) => setupInitialCodeTab(value, codeElement)

    function setupInitialCodeTab(tab: string, codeElement: EditorElement){
        const flow = CODE_PREFIX === tab
        if(!flow && !tab.startsWith(`${CODE_PREFIX}-`)){
            return
        }
        const filePath = flow ? "Flow.yaml" : tab.substring(5)
        const editorTab = getTabPropsFromFilePath(filePath, flow)
        return flow ? codeElement : getTabFromFilesTab(editorTab)
    }

    return {setupInitialCodeTab}
}

export function useFilesPanels(panels: Ref<Panel[]>, namespace: Ref<string | undefined>) {
    function focusTab(tabValue: string){
        for(const panel of panels.value){
            const t = panel.tabs.find(e => e.uid === tabValue);
            if(t) panel.activeTab = t;
        }
    }

    provide(FILES_OPEN_TAB_INJECTION_KEY, (tab) => {
        if(!tab.path){
            return
        }
        const uid = generateUid(tab)
        const existing = panels.value.some(p => p.tabs.some(t => t.uid === uid))
        if(!existing){
            const panelTab = getTabFromFilesTab(tab)
            const firstPanelWithCodeTab = panels.value.find(p => p.tabs.some(t => t.uid.startsWith("code")))
            if(firstPanelWithCodeTab){
                firstPanelWithCodeTab.tabs.push(panelTab)
                firstPanelWithCodeTab.activeTab = panelTab
            }else{
                panels.value.push({
                    activeTab: panelTab,
                    tabs: [panelTab],
                    size: defaultSize.value,
                })
            }
        }
        focusTab(generateUid(tab))
    })

    provide(FILES_CLOSE_TAB_INJECTION_KEY, (tab) => {
        const uid = generateUid(tab)
        for(const panel of panels.value){
            if(panel.tabs.some(e => e.uid === uid)){
                panel.tabs = panel.tabs.filter(e => e.uid !== uid);
            }
        }
    })

    provide(FILES_SET_DIRTY_INJECTION_KEY, ({path, dirty}) => {
        const uid = generateUid({path})
        const tab = panels.value.flatMap(p => p.tabs).find(t => t.uid === uid) as TabLive
        if(tab){
            tab.dirty = dirty
        }
    })

    provide(FILES_UPDATE_CONTENT_INJECTION_KEY, ({path, content}) => {
        const uid = generateUid({path})
        const tab = panels.value.flatMap(p => p.tabs).find(t => t.uid === uid) as TabLiveWithContent
        if(tab){
            tab.content = content
            tab.path = path
        }
    })

    const namespacesStore = useNamespacesStore();

    // on save all files, save all files
    // and set all tabs as not dirty
    provide(FILES_SAVE_ALL_INJECTION_KEY, async () => {
        for(const panel of panels.value){
            for(const tab of panel.tabs as TabLiveWithContent[]){
                if(!tab.content || !tab.path){
                    continue
                }
                if(namespace.value === undefined){
                    throw new Error(`Cannot save file "${tab.path}": namespace is undefined`)
                }
                await namespacesStore.createFile({
                    namespace: namespace.value,
                    path: tab.path,
                    content: tab.content
                });
                tab.dirty = false;
            }
        }
    });

    const defaultSize = computed(() => panels.value.length === 0 ? 1 : (panels.value.reduce((acc, p) => acc + (p.size ?? 0), 0) * 100 / panels.value.length))
}