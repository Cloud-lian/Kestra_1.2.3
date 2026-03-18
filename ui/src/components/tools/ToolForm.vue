<template>
    <div class="tool-form-wrapper">
        <el-card class="tool-form-card" shadow="always">
            <!-- Step progress bar -->
            <div class="step-progress">
                <div
                    v-for="(step, index) in steps"
                    :key="index"
                    class="step-col"
                    @click="goToStep(index)"
                >
                    <div class="step-track-row">
                        <div class="step-line" :class="{filled: index <= activeStep}" v-if="index > 0" />
                        <div class="step-line empty" v-else />
                        <div
                            class="step-circle"
                            :class="{filled: index <= activeStep, current: index === activeStep}"
                        >
                            {{ index + 1 }}
                        </div>
                        <div class="step-line" :class="{filled: index < activeStep}" v-if="index < steps.length - 1" />
                        <div class="step-line empty" v-else />
                    </div>
                    <span class="step-label" :class="{active: index <= activeStep}">
                        {{ step }}
                    </span>
                </div>
            </div>

            <!-- Form -->
            <el-form
                ref="formRef"
                :model="form"
                :rules="rules"
                labelPosition="top"
                @submit.prevent
                class="tool-form-body"
            >
                <!-- Step 1: Identity -->
                <div v-show="activeStep === 0">
                    <el-form-item :label="$t('tools.fields.name')" prop="name">
                        <el-input v-model="form.name" :placeholder="$t('tools.fields.namePlaceholder')" />
                        <div class="form-help">
                            {{ $t('tools.fields.nameHelp') }}
                        </div>
                    </el-form-item>

                    <el-form-item :label="$t('tools.fields.title')" prop="title">
                        <el-input v-model="form.title" :placeholder="$t('tools.fields.titlePlaceholder')" />
                    </el-form-item>

                    <el-form-item :label="$t('description')" prop="description">
                        <el-input v-model="form.description" type="textarea" :rows="3" />
                    </el-form-item>

                    <el-form-item :label="$t('tools.fields.tags')">
                        <el-select
                            v-model="form.tags"
                            multiple
                            filterable
                            allowCreate
                            defaultFirstOption
                            :placeholder="$t('tools.fields.tags')"
                            style="width: 100%"
                        />
                    </el-form-item>
                </div>

                <!-- Step 2: Link Flow -->
                <div v-show="activeStep === 1">
                    <el-form-item :label="$t('namespace')" prop="namespace">
                        <NamespaceSelect v-model="form.namespace" />
                    </el-form-item>

                    <el-form-item :label="$t('tools.fields.flowId')" prop="flowId">
                        <el-select
                            v-model="form.flowId"
                            filterable
                            clearable
                            style="width: 100%"
                            :placeholder="form.namespace ? $t('search') : $t('tools.selectNamespaceFirst')"
                            :disabled="!form.namespace"
                            :loading="loadingFlows"
                        >
                            <el-option
                                v-for="id in flowIds"
                                :key="id"
                                :label="id"
                                :value="id"
                            />
                        </el-select>
                    </el-form-item>
                </div>

                <!-- Step 3: Annotations -->
                <div v-show="activeStep === 2">
                    <p class="form-help mb-4">
                        {{ $t('tools.annotations.description') }}
                    </p>

                    <div class="annotation-item">
                        <div class="annotation-header">
                            <el-switch v-model="form.annotations.readOnlyHint" @change="onReadOnlyChange" />
                            <div>
                                <span class="annotation-label">{{ $t('tools.annotations.readOnlyHint') }}</span>
                                <div class="form-help">
                                    {{ $t('tools.annotations.readOnlyHintHelp') }}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="annotation-item">
                        <div class="annotation-header">
                            <el-switch v-model="form.annotations.openWorldHint" />
                            <div>
                                <span class="annotation-label">{{ $t('tools.annotations.openWorldHint') }}</span>
                                <div class="form-help">
                                    {{ $t('tools.annotations.openWorldHintHelp') }}
                                </div>
                            </div>
                        </div>
                    </div>

                    <el-divider v-if="!form.annotations.readOnlyHint" />

                    <p v-if="form.annotations.readOnlyHint" class="form-help read-only-note">
                        {{ $t('tools.annotations.readOnlyDisabledNote') }}
                    </p>

                    <div class="annotation-item" :class="{disabled: form.annotations.readOnlyHint}">
                        <div class="annotation-header">
                            <el-switch
                                v-model="form.annotations.destructiveHint"
                                :disabled="form.annotations.readOnlyHint"
                            />
                            <div>
                                <span class="annotation-label">{{ $t('tools.annotations.destructiveHint') }}</span>
                                <div class="form-help">
                                    {{ $t('tools.annotations.destructiveHintHelp') }}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="annotation-item" :class="{disabled: form.annotations.readOnlyHint}">
                        <div class="annotation-header">
                            <el-switch
                                v-model="form.annotations.idempotentHint"
                                :disabled="form.annotations.readOnlyHint"
                            />
                            <div>
                                <span class="annotation-label">{{ $t('tools.annotations.idempotentHint') }}</span>
                                <div class="form-help">
                                    {{ $t('tools.annotations.idempotentHintHelp') }}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Navigation buttons -->
                <div class="tool-form-actions">
                    <el-button v-if="activeStep > 0" @click="activeStep--">
                        {{ $t("tools.steps.previous") }}
                    </el-button>
                    <el-button v-if="activeStep < steps.length - 1" type="primary" @click="nextStep">
                        {{ $t("tools.steps.next") }}
                    </el-button>
                    <el-button v-if="activeStep === steps.length - 1" type="primary" @click="onSubmit">
                        {{ $t("save") }}
                    </el-button>
                </div>
            </el-form>
        </el-card>
    </div>
</template>

<script setup lang="ts">
    import {ref, reactive, watch, computed} from "vue";
    import {useI18n} from "vue-i18n";
    import type {FormInstance, FormRules} from "element-plus";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import {useFlowStore} from "../../stores/flow";
    import type {ToolData, ToolAnnotations} from "../../stores/tool";

    interface ToolFormData extends Omit<ToolData, "annotations"> {
        annotations: Required<ToolAnnotations>;
    }

    const props = defineProps<{
        initialData?: ToolData;
        isCreate: boolean;
    }>();

    const emit = defineEmits<{
        submit: [data: ToolData];
    }>();

    const {t} = useI18n({useScope: "global"});
    const flowStore = useFlowStore();
    const formRef = ref<FormInstance>();
    const flowIds = ref<string[]>([]);
    const loadingFlows = ref(false);
    const activeStep = ref(0);

    const steps = computed(() => [
        t("tools.steps.identity"),
        t("tools.steps.linkFlow"),
        t("tools.steps.annotations"),
    ]);

    const form = reactive<ToolFormData>({
        id: "",
        name: "",
        title: "",
        description: "",
        namespace: "",
        flowId: "",
        tags: [],
        annotations: {
            readOnlyHint: false,
            destructiveHint: true,
            idempotentHint: false,
            openWorldHint: true,
        },
        enabled: true,
    });

    watch(
        () => props.initialData,
        (data) => {
            if (data) {
                Object.assign(form, {
                    ...data,
                    tags: data.tags ?? [],
                    annotations: {
                        readOnlyHint: false,
                        destructiveHint: true,
                        idempotentHint: false,
                        openWorldHint: true,
                        ...(data.annotations ?? {}),
                    },
                });
            }
        },
        {immediate: true}
    );

    // Load flows when namespace changes
    watch(
        () => form.namespace,
        async (namespace, oldNamespace) => {
            if (!namespace) {
                flowIds.value = [];
                return;
            }
            loadingFlows.value = true;
            try {
                const flows = await flowStore.flowsByNamespace(namespace);
                flowIds.value = flows.map((flow: any) => flow.id);
            } catch {
                flowIds.value = [];
            } finally {
                loadingFlows.value = false;
            }
            if (oldNamespace && oldNamespace !== namespace) {
                form.flowId = "";
            }
        },
        {immediate: true}
    );

    const namePattern = /^[a-zA-Z0-9][a-zA-Z0-9_-]*$/;

    const stepFields: Record<number, string[]> = {
        0: ["name"],
        1: ["namespace", "flowId"],
    };

    const rules = reactive<FormRules>({
        name: [
            {required: true, message: t("tools.validation.nameRequired"), trigger: "blur"},
            {pattern: namePattern, message: t("tools.validation.namePattern"), trigger: "blur"},
        ],
        namespace: [{required: true, message: t("tools.validation.namespaceRequired"), trigger: "blur"}],
        flowId: [{required: true, message: t("tools.validation.flowIdRequired"), trigger: "blur"}],
    });

    const onReadOnlyChange = (value: boolean) => {
        if (value) {
            form.annotations.destructiveHint = false;
            form.annotations.idempotentHint = false;
        }
    };

    const goToStep = async (step: number) => {
        if (step < activeStep.value) {
            activeStep.value = step;
            return;
        }
        for (let i = activeStep.value; i < step; i++) {
            const fields = stepFields[i];
            if (fields && formRef.value) {
                try {
                    await formRef.value.validateField(fields);
                } catch {
                    activeStep.value = i;
                    return;
                }
            }
        }
        activeStep.value = step;
    };

    const nextStep = async () => {
        if (!formRef.value) return;
        const fields = stepFields[activeStep.value];
        if (fields) {
            try {
                await formRef.value.validateField(fields);
                activeStep.value++;
            } catch {
                // validation failed
            }
        } else {
            activeStep.value++;
        }
    };

    const onSubmit = async () => {
        if (!formRef.value) return;
        try {
            const valid = await formRef.value.validate();
            if (valid) {
                emit("submit", {...form});
            }
        } catch {
            // validation failed
        }
    };
</script>

<style lang="scss" scoped>
    .tool-form-wrapper {
        display: flex;
        justify-content: center;
        padding: 1rem 0;
    }

    .tool-form-card {
        width: 100%;
        max-width: 680px;
        border-radius: 12px;

        :deep(.el-card__body) {
            padding: 2rem 2.5rem;
        }
    }

    /* Step progress bar */
    .step-progress {
        display: flex;
        padding: 0.5rem 0 1rem;
    }

    .step-col {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        cursor: pointer;
    }

    .step-track-row {
        display: flex;
        align-items: center;
        width: 100%;
    }

    .step-line {
        height: 3px;
        flex: 1;
        background-color: var(--el-border-color-lighter);
        transition: background-color 0.3s;

        &.filled {
            background-color: var(--el-color-primary);
        }

        &.empty {
            visibility: hidden;
        }
    }

    .step-circle {
        width: 42px;
        height: 42px;
        min-width: 42px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        font-weight: 600;
        border: 3px solid var(--el-border-color-lighter);
        color: var(--el-text-color-placeholder);
        background: var(--el-bg-color);
        transition: all 0.3s;

        &.filled {
            border-color: var(--el-color-primary);
            background-color: var(--el-color-primary);
            color: #fff;
        }

        &.current {
            box-shadow: 0 0 0 4px var(--el-color-primary-light-8);
        }
    }

    .step-label {
        margin-top: 10px;
        font-size: 0.9rem;
        color: var(--el-text-color-secondary);
        transition: color 0.3s;
        user-select: none;
        white-space: nowrap;

        &.active {
            color: var(--el-color-primary);
            font-weight: 600;
        }
    }

    /* Form body */
    .tool-form-body {
        min-height: 280px;
        padding-top: 0.5rem;
    }

    .form-help {
        font-size: 0.8rem;
        color: var(--el-text-color-secondary);
        line-height: 1.4;
        margin-top: 4px;
    }

    .read-only-note {
        padding: 8px 12px;
        margin-bottom: 16px;
        border-radius: 6px;
        background-color: var(--el-color-info-light-9);
    }

    /* Annotation items */
    .annotation-item {
        padding: 14px 16px;
        border-radius: 8px;
        border: 1px solid var(--el-border-color-lighter);
        margin-bottom: 12px;
        transition: opacity 0.2s;

        &.disabled {
            opacity: 0.45;
        }
    }

    .annotation-header {
        display: flex;
        align-items: flex-start;
        gap: 14px;

        .el-switch {
            margin-top: 2px;
        }
    }

    .annotation-label {
        font-weight: 500;
        font-size: 0.9rem;
        line-height: 1.4;
    }

    /* Navigation */
    .tool-form-actions {
        display: flex;
        justify-content: center;
        gap: 12px;
        padding-top: 1.5rem;
        margin-top: 1rem;
        border-top: 1px solid var(--el-border-color-extra-light);
    }
</style>
