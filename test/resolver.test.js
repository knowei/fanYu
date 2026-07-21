import test from "node:test";
import assert from "node:assert/strict";
import {
  chooseEpisode,
  chooseSearchResult,
  decodePlayerUrl,
  extractNumber
} from "../src/resolver.js";

test("extracts source and episode ids from a play URL", () => {
  const url = "https://example.test/vod/play/id/372813/sid/3/nid/15.html";
  assert.equal(extractNumber(url, "/sid/(\\d+)/"), 3);
  assert.equal(extractNumber(url, "/nid/(\\d+)\\.html"), 15);
});

test("prefers an exact normalized title and otherwise uses the first result", () => {
  const results = [
    { title: "相似作品", detailUrl: "first" },
    { title: "关于我转生 变成史莱姆", detailUrl: "exact" }
  ];
  assert.equal(chooseSearchResult(results, "关于我转生变成史莱姆").detailUrl, "exact");
  assert.equal(chooseSearchResult(results, "不存在的标题").detailUrl, "first");
});

test("selects the requested episode from the first source", () => {
  const items = [
    { sourceId: 3, episode: 1, pageUrl: "source-3-1" },
    { sourceId: 3, episode: 2, pageUrl: "source-3-2" },
    { sourceId: 4, episode: 1, pageUrl: "source-4-1" }
  ];
  assert.equal(chooseEpisode(items, 2).pageUrl, "source-3-2");
});

test("decodes supported player URL encodings", () => {
  const url = "https://cdn.example/video/index.m3u8?token=a b";
  assert.equal(decodePlayerUrl(url, 0), url);
  assert.equal(decodePlayerUrl(encodeURIComponent(url), 1), url);
  assert.equal(decodePlayerUrl(Buffer.from(encodeURIComponent(url)).toString("base64"), 2), url);
});
