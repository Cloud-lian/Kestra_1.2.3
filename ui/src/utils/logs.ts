import {cssVariable} from "@kestra-io/ui-libs";

const LEVELS = [
    "ERROR",
    "WARN",
    "INFO",
    "DEBUG",
    "TRACE"
];

export function color() {
    return Object.fromEntries(LEVELS.map(level => [level, cssVariable("--log-chart-" + level.toLowerCase())])) as Record<typeof LEVELS[number], string>;
}

const COLORS: Record<typeof LEVELS[number], string> = {
    ERROR: "#AB0009",
    WARN: "#DD5F00",
    INFO: "#029E73",
    DEBUG: "#1761FD",
    TRACE: "#8405FF",
};

export function graphColors(state: typeof LEVELS[number]) {
    return COLORS[state];
}

export function chartColorFromLevel(level: typeof LEVELS[number], alpha = 1) {
    const hex = color()[level];
    if (!hex) {
        return null;
    }

    const [r, g, b] = hex.match(/\w\w/g)?.map(x => parseInt(x, 16)) ?? [];
    return `rgba(${r},${g},${b},${alpha})`;
}

export function sort(value: Record<string, any>) {
    return Object.keys(value)
        .sort((a, b) => {
            return index(LEVELS, a) - index(LEVELS, b);
        })
        .reduce(
            (obj, key) => {
                obj[key] = value[key];
                return obj;
            },
            {} as Record<string, any>
        );
}

export function index(based: string[], value: string) {
    const index = based.indexOf(value);

    return index === -1 ? Number.MAX_SAFE_INTEGER : index;
}

export function levelOrLower(level: typeof LEVELS[number]) {
    const levels = [];
    for (const currentLevel of LEVELS) {
        levels.push(currentLevel);
        if (currentLevel === level) {
            break;
        }
    }
    return levels.reverse();
}
