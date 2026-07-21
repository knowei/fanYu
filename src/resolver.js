export function extractNumber(url, pattern) {
  const match = url.match(new RegExp(pattern));
  return match ? Number(match[1]) : null;
}

export function decodePlayerUrl(rawUrl, encrypt = 0) {
  if (!rawUrl) {
    throw new Error("播放器没有返回视频地址");
  }

  switch (Number(encrypt)) {
    case 1:
      return decodeURIComponent(rawUrl);
    case 2:
      return decodeURIComponent(Buffer.from(rawUrl, "base64").toString("utf8"));
    default:
      return rawUrl;
  }
}

export function chooseSearchResult(results, requestedName) {
  if (results.length === 0) {
    throw new Error(`没有搜索到：${requestedName}`);
  }

  const normalized = requestedName.replace(/\s+/g, "").toLowerCase();
  return results.find(result =>
    result.title.replace(/\s+/g, "").toLowerCase() === normalized
  ) ?? results[0];
}

export function chooseEpisode(items, requestedEpisode) {
  const validItems = items.filter(item => item.sourceId !== null && item.episode !== null);
  if (validItems.length === 0) {
    throw new Error("详情页没有可用剧集");
  }

  const firstSourceId = validItems[0].sourceId;
  const selected = validItems.find(item =>
    item.sourceId === firstSourceId && item.episode === requestedEpisode
  );

  if (!selected) {
    throw new Error(`默认线路中没有第 ${requestedEpisode} 集`);
  }

  return selected;
}
