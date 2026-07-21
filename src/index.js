import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";
import {
  chooseEpisode,
  chooseSearchResult,
  decodePlayerUrl,
  extractNumber
} from "./resolver.js";

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const config = JSON.parse(
  await fs.readFile(path.join(currentDir, "enlienli.json"), "utf8")
);

function parseArguments(argv) {
  const options = { headless: false };
  const values = [];

  for (const argument of argv) {
    if (argument === "--headless") {
      options.headless = true;
    } else {
      values.push(argument);
    }
  }

  const name = values[0];
  const episode = Number(values[1]);
  if (!name || !Number.isInteger(episode) || episode < 1) {
    throw new Error('用法：npm run resolve -- "番剧名称" 集数 [--headless]');
  }

  return { name, episode, headless: options.headless };
}

async function waitForContent(page, selector, description) {
  try {
    await page.waitForSelector(selector, { timeout: 60_000 });
  } catch {
    throw new Error(
      `${description}加载失败。若浏览器显示 Cloudflare 验证，请完成验证后重新运行。`
    );
  }
}

async function resolveVideo({ name, episode, headless }) {
  const profileDir = path.resolve(".browser-profile");
  const context = await chromium.launchPersistentContext(profileDir, {
    channel: "msedge",
    headless,
    viewport: { width: 1280, height: 900 }
  });

  try {
    const page = context.pages()[0] ?? await context.newPage();
    const searchUrl = new URL(
      config.search.url.replace("{name}", encodeURIComponent(name)),
      config.baseUrl
    ).href;

    await page.goto(searchUrl, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await waitForContent(page, config.search.resultSelector, "搜索结果");

    const results = await page.locator(config.search.resultSelector).evaluateAll(elements =>
      elements.map(element => ({
        title: element.querySelector("img")?.getAttribute("alt")?.trim() ?? "",
        detailUrl: element.href
      }))
    );
    const searchResult = chooseSearchResult(results, name);

    await page.goto(searchResult.detailUrl, {
      waitUntil: "domcontentloaded",
      timeout: 60_000
    });
    await waitForContent(page, config.episodes.selector, "剧集列表");

    const sourcePattern = config.episodes.sourceRegex;
    const episodePattern = config.episodes.episodeRegex;
    const episodeLinks = await page.locator(config.episodes.selector).evaluateAll(elements =>
      elements.map(element => ({
        name: element.textContent?.trim() ?? "",
        pageUrl: element.href
      }))
    );
    const episodes = episodeLinks.map(item => ({
      ...item,
      sourceId: extractNumber(item.pageUrl, sourcePattern),
      episode: extractNumber(item.pageUrl, episodePattern)
    }));
    const selectedEpisode = chooseEpisode(episodes, episode);

    await page.goto(selectedEpisode.pageUrl, {
      waitUntil: "domcontentloaded",
      timeout: 60_000
    });
    await page.waitForFunction(
      variable => typeof window[variable] === "object",
      config.video.variable,
      { timeout: 60_000 }
    );

    const player = await page.evaluate(variable => window[variable], config.video.variable);
    const videoUrl = decodePlayerUrl(
      player[config.video.urlField],
      player[config.video.encryptField]
    );

    return {
      name: searchResult.title || name,
      episode,
      sourceId: String(selectedEpisode.sourceId),
      detailUrl: searchResult.detailUrl,
      episodeUrl: selectedEpisode.pageUrl,
      videoUrl,
      mediaType: videoUrl.includes(".m3u8") ? "hls" : "unknown"
    };
  } finally {
    await context.close();
  }
}

try {
  const input = parseArguments(process.argv.slice(2));
  const result = await resolveVideo(input);
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
