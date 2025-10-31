<template>
    <DynamicScroller
        v-if="followedExecution"
        ref="taskRunScroller"
        :items="currentTaskRuns"
        :minItemSize="50"
        keyField="id"
        class="log-wrapper"
    >
        <template #default="{item: currentTaskRun, index: currentTaskRunIndex, active: isTaskRunActive}">
            <DynamicScrollerItem
                v-if="uniqueTaskRunDisplayFilter(currentTaskRun)"
                :item="currentTaskRun"
                :active="isTaskRunActive"
                :data-index="currentTaskRunIndex"
            >
                <el-card class="attempt-wrapper">
                    <TaskRunLine
                        :currentTaskRun="currentTaskRun"
                        :followedExecution="followedExecution"
                       
                        :forcedAttemptNumber="forcedAttemptNumber"
                        :taskRunId="taskRunId"
                        :selectedAttemptNumberByTaskRunId="selectedAttemptNumberByTaskRunId"
                        :shownAttemptsUid="shownAttemptsUid"
                        :logs="filteredLogs"
                        @toggle-show-attempt="toggleShowAttempt"
                        @swap-displayed-attempt="swapDisplayedAttempt"
                        @update-logs="loadLogs"
                    >
                        <template #buttons>
                            <div id="buttons" />
                        </template>
                    </TaskRunLine>
                    <ForEachStatus
                        v-if="shouldDisplayProgressBar(currentTaskRun)"
                        :executionId="currentTaskRun.executionId"
                        :subflowsStatus="forEachItemExecutableByRootTaskId[currentTaskRun.taskId].outputs.iterations"
                        :max="forEachItemExecutableByRootTaskId[currentTaskRun.taskId].outputs.numberOfBatches"
                    />
                    <DynamicScroller
                        v-if="shouldDisplayLogs(currentTaskRun)"
                        :items="logsWithIndexByAttemptUid[attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])] ?? []"
                        :minItemSize="1"
                        keyField="index"
                        class="log-lines"
                        :class="{'single-line': currentTaskRuns.length === 1}"
                        :ref="(el:InstanceType<typeof DynamicScroller>) => logsScrollerRef(el, currentTaskRunIndex, attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
                        @resize="scrollToBottomFailedTask"
                    >
                        <template #default="{item, index, active}">
                            <DynamicScrollerItem
                                :item="item"
                                :active="active"
                                :sizeDependencies="[item.message, item.image]"
                                :data-index="index"
                            >
                                <Teleport v-if="item.logFile" to="#buttons">
                                    <el-button-group class="line">
                                        <el-button
                                            type="primary"
                                            tag="a"
                                            :href="fileUrl(item.logFile)"
                                            target="_blank"
                                            size="small"
                                            :icon="Download"
                                            rel="noopener noreferrer"
                                        >
                                            {{ $t('download') }}
                                        </el-button>
                                        <FilePreview :value="item.logFile" :executionId="followedExecution.id" />
                                        <el-button disabled size="small" type="primary" v-if="logFileSizeByPath[item.logFile]">
                                            ({{ logFileSizeByPath[item.logFile] }})
                                        </el-button>
                                    </el-button-group>
                                </Teleport>
                                <LogLine
                                    class="line"
                                    :cursor="logCursor === `${currentTaskRunIndex}/${index}`"
                                    :class="{['log-bg-' + levelToHighlight?.toLowerCase()]: levelToHighlight === item.level, 'opacity-40': levelToHighlight && levelToHighlight !== item.level}"
                                    :key="index"
                                    :level="level"
                                    :log="item"
                                    :excludeMetas="excludeMetas"
                                    v-else-if="!filter || filter === '' || item.message?.toLowerCase().includes(filter.toLowerCase())"
                                />
                                <TaskRunDetails
                                    v-if="!taskRunId && isSubflow(currentTaskRun) && shouldDisplaySubflow(index, currentTaskRun) && currentTaskRun.outputs?.executionId"
                                    :ref="el => subflowTaskRunDetailsRef(el, currentTaskRunIndex + '/' + index)"
                                    :logCursor="logCursor?.split('/')?.slice(2).join('/')"
                                    @log-cursor="emitLogCursor(currentTaskRunIndex + '/' + index + '/' + $event)"
                                    @log-indices-by-level="childLogIndicesByLevel(currentTaskRunIndex, index, $event)"
                                    :levelToHighlight="levelToHighlight"
                                    :level="level"
                                    :excludeMetas="['namespace', 'flowId', 'taskId', 'executionId']"
                                    :filter="filter"
                                    :allowAutoExpandSubflows="false"
                                    :targetExecutionId="currentTaskRun.outputs.executionId"
                                    :class="$el.classList.contains('even') ? '' : 'even'"
                                    :showProgressBar="showProgressBar"
                                    :showLogs="showLogs"
                                />
                            </DynamicScrollerItem>
                        </template>
                    </DynamicScroller>
                </el-card>
            </DynamicScrollerItem>
        </template>
    </DynamicScroller>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, onBeforeUnmount, nextTick, useTemplateRef} from "vue";
    import Download from "vue-material-design-icons/Download.vue";
    import LogLine from "./LogLine.vue";
    import {State} from "@kestra-io/ui-libs";
    import _xor from "lodash/xor";
    import _groupBy from "lodash/groupBy";
    import moment from "moment";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css";
    import {logDisplayTypes} from "../../utils/constants";
    // @ts-expect-error no types for dynamic scroller
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";
    import {useCoreStore} from "../../stores/core";
    import {useExecutionsStore} from "../../stores/executions";
    import ForEachStatus from "../executions/ForEachStatus.vue";
    // @ts-expect-error no types for TaskRunLine
    import TaskRunLine from "../executions/TaskRunLine.vue";
    import * as FlowUtils from "../../utils/flowUtils";
    import FilePreview from "../executions/FilePreview.vue";
    import {apiUrl} from "override/utils/route";
    import Utils from "../../utils/utils";
    import * as LogUtils from "../../utils/logs";
    import throttle from "lodash/throttle";
    import {useAxios} from "../../utils/axios";
    import {useI18n} from "vue-i18n";
    const {t} = useI18n();
    const props = defineProps<{
        logCursor?: string,
        levelToHighlight?: string,
        level?: string,
        filter?: string,
        taskRunId?: string,
        excludeMetas?: string[],
        forcedAttemptNumber?: number,
        targetExecutionId?: string,
        targetFlow?: any,
        allowAutoExpandSubflows?: boolean,
        showProgressBar?: boolean,
        showLogs?: boolean
    }>();

    const emit = defineEmits([
        "opened-taskruns-count",
        "follow",
        "reset-expand-collapse-all-switch",
        "log-cursor",
        "log-indices-by-level"
    ]);

    const coreStore = useCoreStore();
    const executionsStore = useExecutionsStore();

    const shownAttemptsUid = ref<string[]>([]);
    const rawLogs = ref<any[]>([]);
    const timer = ref<any>(undefined);
    const timeout = ref<any>(undefined);
    const selectedAttemptNumberByTaskRunId = ref<Record<string, number>>({});
    const executionSSE = ref<any>(undefined);
    const logsSSE = ref<any>(undefined);
    const flow = ref<any>(undefined);
    const logsBuffer = ref<any[]>([]);
    const shownSubflowsIds = ref<any[]>([]);
    const logFileSizeByPath = ref<Record<string, any>>({});
    const childrenLogIndicesByLevelByChildUid = ref<Record<string, any>>({});
    const logsScrollerRefs = ref<Record<string, any>>({});
    const subflowTaskRunDetailsRefs = ref<Record<string, any>>({});
    const throttledExecutionUpdate = ref<any>(undefined);
    const targetExecution = ref<any>(undefined);

    // Computed
    const followedExecution = computed(() =>
        props.targetExecutionId === undefined ? executionsStore.execution : targetExecution.value
    );

    const currentTaskRuns = computed(() =>
        followedExecution.value?.taskRunList?.filter((tr: any) => props.taskRunId ? tr.id === props.taskRunId : true) ?? []
    );

    const taskRunById = computed(() =>
        Object.fromEntries(currentTaskRuns.value.map((taskRun: any) => [taskRun.id, taskRun]))
    );

    const logsWithIndexByAttemptUid = computed(() => {
        const logFilesWrappers = currentTaskRuns.value.flatMap((taskRun: any) =>
            attempts(taskRun)
                .filter((attempt: any) => attempt.logFile !== undefined)
                .map((attempt: any, attemptNumber: number) => ({logFile: attempt.logFile, taskRunId: taskRun.id, attemptNumber}))
        );
        logFilesWrappers.forEach((logFileWrapper: any) => fetchAndStoreLogFileSize(logFileWrapper.logFile));
        const indexedLogs = [...filteredLogs.value, ...logFilesWrappers]
            .filter((logLine: any) => logLine.logFile !== undefined || (props.filter === "" || logLine?.message?.toLowerCase().includes(props.filter?.toLowerCase() ?? "") || isSubflow(taskRunById.value[logLine.taskRunId])))
            .map((logLine: any, index: number) => ({...logLine, index}));
        return _groupBy(indexedLogs, (indexedLog: any) => attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber));
    });

    const autoExpandTaskRunStates = computed(() => {
        switch (localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT) {
        case logDisplayTypes.ERROR:
            return [State.FAILED, State.RUNNING, State.PAUSED];
        case logDisplayTypes.ALL:
            return State.arrayAllStates().map((s: any) => s.name);
        case logDisplayTypes.HIDDEN:
            return [];
        default:
            return State.arrayAllStates().map((s: any) => s.name);
        }
    });

    const taskTypeAndTaskRunByTaskId = computed(() =>
        Object.fromEntries(followedExecution.value?.taskRunList?.map((taskRun: any) => [taskRun.taskId, [taskType(taskRun), taskRun]]))
    );

    const forEachItemExecutableByRootTaskId = computed(() =>
        Object.fromEntries(
            Object.entries(taskTypeAndTaskRunByTaskId.value)
                .filter(([, taskTypeAndTaskRun]: any) => taskTypeAndTaskRun[0] === "io.kestra.plugin.core.flow.ForEachItem" || taskTypeAndTaskRun[0] === "io.kestra.core.tasks.flows.ForEachItem")
                .map(([taskId]: any) => [taskId, taskTypeAndTaskRunByTaskId.value?.[taskId + "_items"]?.[1]])
        )
    );

    const currentTaskRunsLogIndicesByLevel = computed(() =>
        currentTaskRuns.value.reduce((currentTaskRunsLogIndicesByLevel: any, taskRun: any, taskRunIndex: number) => {
            if (shouldDisplayLogs(taskRun)) {
                const currentTaskRunLogs = logsWithIndexByAttemptUid.value[attemptUid(taskRun.id, selectedAttemptNumberByTaskRunId.value[taskRun.id])];
                currentTaskRunLogs?.forEach((log: any, logIndex: number) => {
                    currentTaskRunsLogIndicesByLevel[log.level] = [...(currentTaskRunsLogIndicesByLevel?.[log.level] ?? []), taskRunIndex + "/" + logIndex];
                });
            }
            return currentTaskRunsLogIndicesByLevel;
        }, {})
    );

    const allLogIndicesByLevel = computed(() => {
        const currentTaskRunsLogIndicesByLevelLocal = {...currentTaskRunsLogIndicesByLevel.value};
        return Object.entries(childrenLogIndicesByLevelByChildUid.value).reduce((allLogIndicesByLevel: any, [logUid, childrenLogIndicesByLevel]: any) => {
            Object.entries(childrenLogIndicesByLevel).forEach(([level, logIndices]: any) => {
                allLogIndicesByLevel[level] = [...(allLogIndicesByLevel?.[level] ?? []), ...logIndices.map((logIndex: any) => logUid + "/" + logIndex)];
            });
            return allLogIndicesByLevel;
        }, currentTaskRunsLogIndicesByLevelLocal);
    });

    const levelOrLower = computed(() => LogUtils.levelOrLower(props.level ?? "INFO"));

    const filteredLogs = computed(() =>
        rawLogs.value.filter((log: any) => levelOrLower.value.includes(log.level))
    );

    // Watchers
    watch(() => shownAttemptsUid.value.length, (openedTaskrunsCount) => {
        emit("opened-taskruns-count", openedTaskrunsCount);
    });

    watch(() => props.level, () => {
        rawLogs.value = [];
        loadLogs(followedExecution.value.id);
    });

    watch(currentTaskRuns, (taskRuns) => {
        selectedAttemptNumberByTaskRunId.value = Object.fromEntries(taskRuns.map((taskRun: any) => [taskRun.id, props.forcedAttemptNumber ?? attempts(taskRun).length - 1]));
        autoExpandBasedOnSettings();
    }, {immediate: true, deep: true});

    watch(() => props.targetFlow, (flowSource) => {
        if (flowSource) {
            flow.value = flowSource;
        }
    }, {immediate: true});

    const taskRunScroller = useTemplateRef("taskRunScroller");

    watch(followedExecution, async (newExecution, oldExecution) => {
        if (!newExecution) return;
        if (!oldExecution) {
            nextTick(() => {
                const parentScroller = (taskRunScroller.value as any)?.$el?.parentNode?.closest(".vue-recycle-scroller");
                if (parentScroller) {
                    const scrollerStyles = window.getComputedStyle(parentScroller);
                    (taskRunScroller.value as any).$el.style.maxHeight = `${Number(scrollerStyles.getPropertyValue("max-height")) - parentScroller.clientHeight}px`;
                }
            });
        }
        if (!props.targetFlow) {
            flow.value = await executionsStore.loadFlowForExecution({
                namespace: newExecution.namespace,
                flowId: newExecution.flowId,
                revision: newExecution.flowRevision,
                store: false
            });
        }
        if (!State.isRunning(followedExecution.value.state.current)) {
            setTimeout(() => closeLogsSSE(), 2000);
            if (!logsSSE.value) {
                loadLogs(newExecution.id);
            }
            return;
        }
        if (!logsSSE.value) {
            followLogs(newExecution.id);
        }
    }, {immediate: true});

    watch(allLogIndicesByLevel, () => {
        emit("log-indices-by-level", allLogIndicesByLevel.value);
    });

    watch(() => props.logCursor, (newValue) => {
        if (newValue !== undefined) {
            scrollToLog(newValue);
        }
    });

    // Lifecycle
    onMounted(() => {
        throttledExecutionUpdate.value = throttle((executionEvent: any) => {
            targetExecution.value = JSON.parse(executionEvent.data);
        }, 500);

        if (props.targetExecutionId) {
            followExecution(props.targetExecutionId);
        }
        autoExpandBasedOnSettings();
    });

    onBeforeUnmount(() => {
        closeLogsSSE();
    });

    // Methods
    function fileUrl(path: string) {
        return `${apiUrl()}/executions/${followedExecution.value.id}/file?path=${path}`;
    }

    const axios = useAxios();

    async function fetchAndStoreLogFileSize(path: string) {
        if (logFileSizeByPath.value[path] !== undefined) return;
        const axiosResponse = await axios.get(`${apiUrl()}/executions/${followedExecution.value.id}/file/metas?path=${path}`, {
            validateStatus: (status: number) => status === 200 || status === 404 || status === 422
        });
        logFileSizeByPath.value[path] = Utils.humanFileSize(axiosResponse.data.size);
    }

    function closeLogsSSE() {
        if (logsSSE.value) {
            logsSSE.value.close();
            logsSSE.value = undefined;
        }
    }

    function autoExpandBasedOnSettings() {
        if (autoExpandTaskRunStates.value.length === 0) return;
        if (followedExecution.value === undefined) {
            setTimeout(() => autoExpandBasedOnSettings(), 50);
            return;
        }
        currentTaskRuns.value.forEach((taskRun: any) => {
            if (isSubflow(taskRun) && props.allowAutoExpandSubflows === false) return;
            if (props.taskRunId === taskRun.id || autoExpandTaskRunStates.value.includes(taskRun.state.current)) {
                showAttempt(attemptUid(taskRun.id, selectedAttemptNumberByTaskRunId.value[taskRun.id]));
            }
        });
    }

    function shouldDisplayProgressBar(taskRun: any) {
        return props.showProgressBar &&
            (taskType(taskRun) === "io.kestra.plugin.core.flow.ForEachItem" || taskType(taskRun) === "io.kestra.core.tasks.flows.ForEachItem") &&
            forEachItemExecutableByRootTaskId.value[taskRun.taskId]?.outputs?.iterations !== undefined &&
            forEachItemExecutableByRootTaskId.value[taskRun.taskId]?.outputs?.numberOfBatches !== undefined;
    }

    function shouldDisplayLogs(taskRun: any) {
        return (props.taskRunId ||
            (shownAttemptsUid.value.includes(attemptUid(taskRun.id, selectedAttemptNumberByTaskRunId.value[taskRun.id])) &&
                logsWithIndexByAttemptUid.value[attemptUid(taskRun.id, selectedAttemptNumberByTaskRunId.value[taskRun.id])])) &&
            props.showLogs;
    }

    function closeTargetExecutionSSE() {
        if (executionSSE.value) {
            executionSSE.value.close();
            executionSSE.value = undefined;
        }
    }

    function followExecution(executionId: string) {
        closeTargetExecutionSSE();
        executionsStore
            .followExecution({id: executionId, rawSSE: true}, t)
            .then((sse: any) => {
                executionSSE.value = sse;
                executionSSE.value.onmessage = (executionEvent: any) => {
                    const isEnd = executionEvent && executionEvent.lastEventId === "end";
                    if (executionEvent.lastEventId !== "start") {
                        throttledExecutionUpdate.value(executionEvent);
                    }
                    if (isEnd) {
                        closeTargetExecutionSSE();
                        throttledExecutionUpdate.value.flush();
                    }
                };
            });
    }

    function followLogs(executionId: string) {
        executionsStore
            .followLogs({id: executionId})
            .then((sse: any) => {
                logsSSE.value = sse;
                logsSSE.value.onmessage = (event: any) => {
                    if (event.lastEventId !== "start") {
                        logsBuffer.value = logsBuffer.value.concat(JSON.parse(event.data));
                    }
                    clearTimeout(timeout.value);
                    timeout.value = setTimeout(() => {
                        timer.value = moment();
                        rawLogs.value = rawLogs.value.concat(logsBuffer.value);
                        logsBuffer.value = [];
                        scrollToBottomFailedTask();
                    }, 100);
                    if (moment().diff(timer.value, "seconds") > 0.5) {
                        clearTimeout(timeout.value);
                        timer.value = moment();
                        rawLogs.value = rawLogs.value.concat(logsBuffer.value);
                        logsBuffer.value = [];
                        scrollToBottomFailedTask();
                    }
                };
                logsSSE.value.onerror = (_: any) => {
                    coreStore.message = {
                        variant: "error",
                        title: t("error"),
                        message: t("something_went_wrong.loading_execution"),
                    };
                };
            });
    }

    function isSubflow(taskRun: any) {
        return taskRun.outputs?.executionId;
    }

    function shouldDisplaySubflow(taskRunIndex: number, taskRun: any) {
        const subflowExecutionId = taskRun.outputs.executionId;
        const index = shownSubflowsIds.value.findIndex(item => item.subflowExecutionId === subflowExecutionId);
        if (index === -1) {
            shownSubflowsIds.value.push({subflowExecutionId: subflowExecutionId, taskRunIndex: taskRunIndex});
            return true;
        } else {
            return shownSubflowsIds.value[index].taskRunIndex === taskRunIndex;
        }
    }

    function attemptUid(taskRunId: string, attemptNumber: number) {
        return `${taskRunId}-${attemptNumber}`;
    }

    function scrollToBottomFailedTask() {
        if (autoExpandTaskRunStates.value.includes(followedExecution.value?.state?.current)) {
            currentTaskRuns.value.forEach((taskRun: any) => {
                if (taskRun.state.current === State.FAILED || taskRun.state.current === State.RUNNING) {
                    const attemptNumber = taskRun.attempts ? taskRun.attempts.length - 1 : (props.forcedAttemptNumber ?? 0);
                    if (shownAttemptsUid.value.includes(`${taskRun.id}-${attemptNumber}`)) {
                        logsScrollerRefs.value?.[`${taskRun.id}-${attemptNumber}`]?.scrollToBottom();
                    }
                }
            });
        }
    }

    function uniqueTaskRunDisplayFilter(currentTaskRun: any) {
        return !(props.taskRunId && props.taskRunId !== currentTaskRun.id);
    }

    function loadLogs(executionId: string) {
        if (!props.showLogs) return;
        executionsStore.loadLogs({
            executionId,
            params: {
                minLevel: props.level
            }
        }).then((logs: any) => {
            rawLogs.value = logs;
        });
    }

    function attempts(taskRun: any) {
        if (followedExecution.value.state.current === State.RUNNING || props.forcedAttemptNumber === undefined) {
            return taskRun.attempts ?? [{state: taskRun.state}];
        }
        return taskRun.attempts ? [taskRun.attempts[props.forcedAttemptNumber]] : [];
    }

    function showAttempt(attemptUidVal: string) {
        if (!shownAttemptsUid.value.includes(attemptUidVal)) {
            shownAttemptsUid.value.push(attemptUidVal);
        }
    }

    function toggleShowAttempt(attemptUidVal: string) {
        shownAttemptsUid.value = _xor(shownAttemptsUid.value, [attemptUidVal]);
    }

    function swapDisplayedAttempt(event: any) {
        const {taskRunId, attemptNumber: newDisplayedAttemptNumber} = event;
        shownAttemptsUid.value = shownAttemptsUid.value.map(attemptUidVal => attemptUidVal.startsWith(`${taskRunId}-`)
            ? attemptUid(taskRunId, newDisplayedAttemptNumber)
            : attemptUidVal
        );
        selectedAttemptNumberByTaskRunId.value[taskRunId] = newDisplayedAttemptNumber;
    }

    function taskType(taskRun: any) {
        if (!taskRun) return undefined;
        const task = FlowUtils.findTaskById(flow.value, taskRun?.taskId);
        const parentTaskRunId = taskRun.parentTaskRunId;
        if (task === undefined && parentTaskRunId) {
            return taskType(taskRunById.value[parentTaskRunId]);
        }
        return task ? task.type : undefined;
    }

    function emitLogCursor(logCursor: string) {
        emit("log-cursor", logCursor);
    }

    function childLogIndicesByLevel(taskRunIndex: number, logIndex: number, logIndicesByLevel: any) {
        childrenLogIndicesByLevelByChildUid.value[`${taskRunIndex}/${logIndex}`] = logIndicesByLevel;
    }

    function logsScrollerRef(el: any, ...ids: string[]) {
        ids.forEach(id => logsScrollerRefs.value[id] = el);
    }

    function subflowTaskRunDetailsRef(el: any, id: string) {
        subflowTaskRunDetailsRefs.value[id] = el;
    }

    function scrollToLog(logId: string) {
        const split = logId.split("/");
        (taskRunScroller.value as any).scrollToItem(split[0]);
        logsScrollerRefs.value?.[split[0]]?.scrollToItem(split[1]);
        if (split.length > 2) {
            subflowTaskRunDetailsRefs.value?.[split[0] + "/" + split[1]]?.scrollToLog(split.slice(2).join("/"));
        }
    }
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .log-wrapper {
        :deep(> .vue-recycle-scroller__item-wrapper > .vue-recycle-scroller__item-view > div) {
            padding-bottom: 1rem;
        }

        :deep(.line) {
            padding-left: 0;
        }

        .attempt-wrapper {
            background-color: var(--ks-background-input);
            margin-bottom: 0;
            border: 1px solid var(--ks-border-primary);

            :deep(.el-card__body) {
                padding: 0;
            }

            .attempt-wrapper & {
                border-radius: .25rem;
            }

            tbody:last-child & {
                border-bottom: 1px solid var(--ks-border-primary);
            }

            .attempt-header {
                padding: 0 .5rem .5rem;
                border-bottom: 1px solid var(--ks-border-primary);
            }

            .line {
                padding: .5rem;
            }
        }

        .output {
            margin-right: 5px;
        }

        pre {
            border: 1px solid var(--light);
            background-color: var(--bs-gray-200);
            padding: 10px;
            margin-top: 5px;
            margin-bottom: 20px;
        }

        .log-lines {
            transition: max-height 0.2s ease-out;
            max-height: 50vh;

            &.single-line {
                max-height: calc(100vh - 250px);
            }

            .line {
                padding: 1rem;

                &.cursor {
                    background-color: var(--bs-gray-300);
                }
            }
        }
    }
</style>
