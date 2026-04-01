import {describe, expect, it, vi, beforeEach} from "vitest"
import {PebbleAutoCompletion, resetExpressionCache} from "../../../src/services/autoCompletionProvider";

const axiosGet = vi.fn();

vi.mock("../../../src/utils/axios", () => ({
    useAxios: () => ({
        get: axiosGet,
    }),
}));

vi.mock("../../../src/override/utils/route", () => ({
    apiUrlWithoutTenants: () => "http://localhost/api/v1",
}));

describe("PebbleAutoCompletion", () => {
    beforeEach(() => {
        axiosGet.mockReset();
        resetExpressionCache();
    });

    it("filterAutoCompletion fetches filters from API", async () => {
        const filters = ["abs", "capitalize", "jq", "toJson", "upper", "yaml"];
        axiosGet.mockResolvedValue({data: filters});

        const provider = new PebbleAutoCompletion();
        const result = await provider.filterAutoCompletion();

        expect(axiosGet).toHaveBeenCalledWith("http://localhost/api/v1/pebble/filters");
        expect(result).toEqual(filters);
    });

    it("functionsWithDefaults fetches functions list from API", async () => {
        const functions = [
            {name: "kv", arguments: [{name: "key", defaultValue: "'my_key'"}, {name: "namespace", defaultValue: "flow.namespace"}, {name: "errorOnMissing", defaultValue: null}]},
            {name: "now", arguments: [{name: "format", defaultValue: null}]},
            {name: "secret", arguments: [{name: "key", defaultValue: "'MY_SECRET'"}]},
            {name: "uuid", arguments: []},
        ];
        axiosGet.mockResolvedValue({data: functions});

        const provider = new PebbleAutoCompletion();
        const result = await provider.functionsWithDefaults();

        expect(axiosGet).toHaveBeenCalledWith("http://localhost/api/v1/pebble/functions");
        expect(result).toEqual(functions);
    });

    it("filterAutoCompletion returns empty array on API error", async () => {
        axiosGet.mockRejectedValue(new Error("Network error"));

        const provider = new PebbleAutoCompletion();
        const result = await provider.filterAutoCompletion();

        expect(result).toEqual([]);
    });

    it("functionsWithDefaults returns empty array on API error", async () => {
        axiosGet.mockRejectedValue(new Error("Network error"));

        const provider = new PebbleAutoCompletion();
        const result = await provider.functionsWithDefaults();

        expect(result).toEqual([]);
    });
});
