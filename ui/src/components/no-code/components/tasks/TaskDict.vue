<template>
    <el-alert
        v-if="duplicatedKeys?.length"
        :title="t('duplicate-pair', {label: t('key'), key: duplicatedKeys[0]})"
        type="error"
        showIcon
        :closable="false"
        class="mb-2"
    />
    <template v-if="componentType">
        <Wrapper v-for="(item, index) in currentValue" :key="index" class="item-wrapper">
            <template #tasks>
                <InputText
                    :modelValue="item[0]"
                    @update:model-value="onKey(index, $event)"
                    margin="m-0"
                    placeholder="Key"
                    :haveError="duplicatedKeys.includes(item[0])"
                />
                <hr>
                <component
                    ref="valueComponent"
                    :is="componentType"
                    :modelValue="item[1]"
                    @update:model-value="onValueChange(index, $event)"
                    :root="getKey(item[0])"
                    :schema="schema.additionalProperties"
                    :required="isRequired(item[0])"
                    :disabled
                    merge
                />
                <div class="delete-container">
                    <button @click="removeItem(index)" class="remove-entry">
                        {{ te(`no_code.remove.${root}`) ? t(`no_code.remove.${root}`) : t('no_code.remove.default') }} <DeleteOutline />
                    </button>
                </div>
            </template>
        </Wrapper>
    </template>
    <template v-else>
        <el-row v-for="(item, index) in currentValue" :key="index" :gutter="10" class="w-100" :data-testid="`task-dict-item-${item[0]}-${index}`">
            <el-col :span="6">
                <InputText
                    :modelValue="item[0]"
                    @update:model-value="onKey(index, $event)"
                    margin="m-0"
                    placeholder="Key"
                    :haveError="duplicatedKeys.includes(item[0])"
                />
            </el-col>
            <el-col :span="16">
                <TaskExpression
                    :modelValue="item[1]"
                    @update:model-value="onValueChange(index, $event)"
                    :root="getKey(item[0])"
                    :schema="schema.additionalProperties"
                    :required="isRequired(item[0])"
                    :disabled
                />
            </el-col>
            <el-col :span="2" class="col align-self-center delete">
                <DeleteOutline @click="removeItem(index)" />
            </el-col>
        </el-row>
    </template>
    <Add v-if="!props.disabled" :disabled="addButtonDisabled" @add="addItem()" />
</template>

<script setup lang="ts">
    import {computed, ref, useTemplateRef, watch, onMounted, h, inject} from "vue";
    import {useI18n} from "vue-i18n";
    import {DeleteOutline} from "../../utils/icons";

    import InputText from "../inputs/InputText.vue";
    import TaskExpression from "./TaskExpression.vue";
    import Add from "../Add.vue";
    
    import debounce from "lodash/debounce";
    import Wrapper from "./Wrapper.vue";
    import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys";

    const {t, te} = useI18n();

    defineOptions({
        inheritAttrs: false,
    });

    const valueComponent = useTemplateRef<any[]>("valueComponent");

    const model = defineModel<Record<string, any>>({
        default: () => ({}),
    });

    const props = withDefaults(defineProps<{
        schema?: any;
        root?: string;
        disabled?: boolean;
    }>(), {
        disabled: false,
        root: undefined,
        schema: () => ({type: "object"})
    });

    const definitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));

    // this convoluted way of importing the getTaskComponent function
    // is necessary to avoid circular dependencies
    // RollDown might fix it down the road but as of now,
    // TaskDict.vue becomes empty in production builds without this lazy loading
    const getTaskComponent = ref<(property: any, key?: string, definitions?: any) => any>(() => {
        return h("div", "Loading...");
    });

    onMounted(async () => {
        getTaskComponent.value = (await import("./getTaskComponent")).default;
    });

    const componentType = computed(() => {
        return props.schema?.additionalProperties ? getTaskComponent.value?.(
            props.schema.additionalProperties, 
            props.root, 
            definitions.value
        ) : undefined;
    });

    const currentValue = ref<[string, any][]>([])

    // this flag will avoid updating the modelValue when the
    // change was initiated in the component itself
    const localEdit = ref(false);

    watch(
        model,
        (newValue) => {
            if(localEdit.value) {
                return;
            }
            localEdit.value = false;
            if(newValue === undefined || newValue === null) {
                currentValue.value = [];
                return;
            }
            currentValue.value = Object.entries(newValue ?? {});
        },
        {
            immediate: true,
            deep: true
        },
    );

    const duplicatedKeys = computed(() => {
        return currentValue.value.map(pair => pair[0])
            .filter((key, index, self) =>
                self.indexOf(key) !== index
            );
    });

    const emitUpdate = debounce(function () {
        if(duplicatedKeys.value?.length > 0) {
            return;
        }
        localEdit.value = true;
        model.value = Object.fromEntries(currentValue.value.filter(pair => pair[0] !== "" && pair[1] !== undefined));
    }, 200);

    function getKey(key: string) {
        return props.root ? `${props.root}.${key}` : key;
    }

    function isRequired(key: string) {
        return props.schema?.required?.includes(key);
    }

    function onKey(key: number, val: string) {
        currentValue.value[key][0] = val;
        emitUpdate()
    }

    function onValueChange(key: number, val: any) {
        currentValue.value[key][1] = val;
        emitUpdate()
    }

    function removeItem(index: number) {
        currentValue.value.splice(index, 1);
        emitUpdate()
    }

    function addItem() {
        if(addButtonDisabled.value) {
            return;
        }
        currentValue.value.push(["", undefined]);
        emitUpdate()
    }

    const addButtonDisabled = computed(() => {
        return currentValue.value.at(-1)?.[0] === "" && currentValue.value.at(-1)?.[1] === undefined;
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.task-container{
    margin-bottom: 1rem;
}

.delete-container{
    display: flex;
    align-items: center;
    margin-left: 1rem;
    justify-content: end;
}

.remove-entry{
    color: var(--ks-content-secondary);
    background-color: var(--ks-button-background-secondary);
    border: none;
    display: flex;
    align-items: center;
    gap: .5rem; 
    opacity: 0.7;
    padding: 0;
    height: .75rem;
    &:hover {
        color: var(--ks-content-secondary);
        opacity: 1;
    }
}

.item-wrapper {
    margin: .25rem 0;
    background-color: var(--ks-background-card);
}
</style>
