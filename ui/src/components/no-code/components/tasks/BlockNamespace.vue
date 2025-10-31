<template>
    <NamespaceSelect
        data-type="flow"
        v-model="model"
        :readOnly="!flowStore.isCreating"
        allowCreate
    />
</template>
<script setup lang="ts">
    import {useFlowStore} from "../../../../stores/flow";
    import NamespaceSelect from "../../../namespaces/components/NamespaceSelect.vue";
    
    const flowStore = useFlowStore();

    const model = defineModel<string | undefined>();
</script>
