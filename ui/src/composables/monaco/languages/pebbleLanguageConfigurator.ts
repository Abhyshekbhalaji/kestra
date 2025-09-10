import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {languages} from "monaco-editor/esm/vs/editor/editor.api";
import AbstractLanguageConfigurator from "./abstractLanguageConfigurator";
import {QUOTE, PebbleAutoCompletion} from "../../../services/autoCompletionProvider.ts";
import RegexProvider from "../../../utils/regex";
import * as YamlUtils from "@kestra-io/ui-libs/flow-yaml-utils";
import {useI18n} from "vue-i18n";
import {ComputedRef} from "vue";
import IPosition = monaco.IPosition;
import IDisposable = monaco.IDisposable;
import IModel = monaco.editor.IModel;
import CompletionItem = languages.CompletionItem;

function propertySuggestion (value: string, position: {
            lineNumber: number,
            startColumn: number,
            endColumn: number
}, kind?: monaco.languages.CompletionItemKind): CompletionItem {
    let label = value.split("(")[0];
    if (label.startsWith(QUOTE) && label.endsWith(QUOTE)) {
        label = label.substring(1, label.length - 1);
    }

    return ({
        kind: kind ?? (value.includes("(") ? monaco.languages.CompletionItemKind.Function : monaco.languages.CompletionItemKind.Property),
        label: label,
        insertText: value,
        insertTextRules: value.includes("${1:") ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
        sortText: value.includes("(") ? "b" + value : "a" + value,
        range: {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: position.startColumn,
            endColumn: position.endColumn
        }
    });
};

const QUOTES = ["\"", "'"];
export function endOfWordColumn (position: IPosition, model: IModel): number{
    return position.column + (model.findNextMatch(
        RegexProvider.beforeSeparator(QUOTES),
        position,
        true,
        false,
        null,
        true
    )?.matches?.[0]?.length ?? 0);
}

export const NO_SUGGESTIONS = {suggestions: []};
export function registerPebbleAutocompletion(
    autoCompletionProviders: IDisposable[],
    autoCompletion: PebbleAutoCompletion,
    languages: string[]
) {
    // Pebble autocompletion
    autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(languages, {
        triggerCharacters: ["{"],
        async provideCompletionItems(model, position) {
            // Not a subfield access
            const rootPebbleVariableMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarRoot + "$", position, true, false, null, true);
            if (rootPebbleVariableMatcher === null || rootPebbleVariableMatcher.matches === null) {
                return NO_SUGGESTIONS;
            }

            const startOfWordColumn = position.column - rootPebbleVariableMatcher.matches[1].length;
            return {
                suggestions: (await (autoCompletion.rootFieldAutoCompletion()))
                    .map(s => propertySuggestion(s, {
                        lineNumber: position.lineNumber,
                        startColumn: startOfWordColumn,
                        endColumn: endOfWordColumn(position, model)
                    }))
            };
        }
    }));
}

export function registerFunctionParametersAutoCompletion(
    autoCompletionProviders: IDisposable[],
    autoCompletion: PebbleAutoCompletion,
    languages: string[]
) {
    autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(languages, {
        triggerCharacters: ["("],
        async provideCompletionItems(model, position) {
            const source = model.getValue();
            const parsed = YamlUtils.parse(source, false);

            const functionMatcher = model.findPreviousMatch(RegexProvider.capturePebbleFunction + "$", position, true, false, null, true);
            if (functionMatcher === null || functionMatcher.matches === null) {
                return NO_SUGGESTIONS;
            }

            const wordStartOffset = functionMatcher.matches?.[3]?.length
                ?? (model.findPreviousMatch(RegexProvider.beforeSeparator(QUOTES) + "$", position, true, false, null, true)?.matches?.[0]?.length)
                ?? 0;
            const startOfWordColumn = position.column - wordStartOffset;
            return {
                suggestions: (await autoCompletion.functionAutoCompletion(
                        parsed,
                        functionMatcher.matches[1],
                        Object.fromEntries(functionMatcher.matches?.[2]?.split(/ *, */)?.map(arg => arg.split(/ *= */)) ?? []))
                ).map(s => {
                    const endColumn = endOfWordColumn(position, model);
                    const suggestion = propertySuggestion(s, {
                        lineNumber: position.lineNumber,
                        startColumn: startOfWordColumn,
                        endColumn: endColumn
                    }, monaco.languages.CompletionItemKind.Value);

                    // If the inserted value is a string (surrounded by quotes), we remove them if there is already one
                    if (suggestion.insertText.startsWith(QUOTE) && suggestion.insertText.endsWith(QUOTE)) {
                        const lineContent = model.getLineContent(position.lineNumber);
                        suggestion.insertText = suggestion.insertText.substring(
                            QUOTES.includes(lineContent.charAt(startOfWordColumn - 2)) ? 1 : 0,
                            suggestion.insertText.length - (QUOTES.includes(lineContent.charAt(endColumn - 1)) ? 1 : 0)
                        );
                    }

                    return suggestion;
                })
            };
        }
    }))
}

export function registerNestedValueAutoCompletion(
    autoCompletionProviders: IDisposable[],
    autoCompletion: PebbleAutoCompletion,
    languages: string[],
    completionSource?: ComputedRef<string | undefined>,
) {
    autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(languages, {
        triggerCharacters: ["."],
        async provideCompletionItems(model, position) {
            const source = model.getValue();
            const parsed = YamlUtils.parse(completionSource?.value ?? source, false);

            const parentFieldMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarParent + "$", position, true, false, null, true);
            if (parentFieldMatcher === null || parentFieldMatcher.matches === null) {
                return NO_SUGGESTIONS;
            }

            const startOfWordColumn = position.column - parentFieldMatcher.matches[2].length;
            return {
                suggestions: (await autoCompletion.nestedFieldAutoCompletion(source, parsed, parentFieldMatcher.matches[1]))
                    .map(s => propertySuggestion(s, {
                        lineNumber: position.lineNumber,
                        startColumn: startOfWordColumn,
                        endColumn: endOfWordColumn(position, model)
                    }))
            };
        }
    }));
}



export class PebbleLanguageConfigurator extends AbstractLanguageConfigurator {
    private readonly _autoCompletion: PebbleAutoCompletion;
    private readonly _completionSource: ComputedRef<string | undefined>;

    constructor(autoCompletion: PebbleAutoCompletion, completionSource: ComputedRef<string | undefined>) {
        super("plaintext-pebble");
        this._autoCompletion = autoCompletion;
        this._completionSource = completionSource;
    }

    configureAutoCompletion(_: ReturnType<typeof useI18n>["t"], ___: monaco.editor.ICodeEditor | undefined) {


        const autoCompletionProviders: IDisposable[] = [];

        const autoCompletion = this._autoCompletion;
        const completionSource = this._completionSource

        registerPebbleAutocompletion(autoCompletionProviders, autoCompletion, ["plaintext-pebble"]);

        registerFunctionParametersAutoCompletion(autoCompletionProviders, autoCompletion, ["plaintext-pebble"]);

        registerNestedValueAutoCompletion(autoCompletionProviders, autoCompletion, ["plaintext-pebble"], completionSource);

        return autoCompletionProviders;
    }
}
