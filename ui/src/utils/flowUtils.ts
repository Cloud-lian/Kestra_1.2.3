export function findTaskById(flow: any, taskId: string) {
        const result = loopOver(flow, (value: any) => {
            if (value instanceof Object) {
                if (value.type !== undefined && value.id === taskId) {
                    return true;
                }
            }

            return false;
        });

        return result.length > 0 ? result[0] : undefined;
    }

export function loopOver(item: any, predicate: (item: any) => boolean, result: any[] | undefined = undefined) {
    if (result === undefined) {
        result = [];
    }

    if (predicate(item)) {
        result.push(item);
    }

    if (Array.isArray(item)) {
        item.flatMap(item => loopOver(item, predicate, result));
    } else if (item instanceof Object) {
        Object.entries(item).flatMap(([_key, value]) => {
            loopOver(value, predicate, result);
        });
    }

    return result;
}
