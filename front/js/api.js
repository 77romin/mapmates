const config = window.APP_CONFIG || {};
const chargerCache = new Map();
const chargerRequests = new Map();
const weatherCache = new Map();

function withQuery(base, params) {
  const url = new URL(base);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, value);
  });
  return url.toString();
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, { signal: AbortSignal.timeout(12000), ...options, headers: { Accept: "application/json", ...(options.headers || {}) } });
  if (!response.ok) throw new Error(`API 요청 실패 (${response.status})`);
  const data = await response.json();
  const code = data?.response?.header?.resultCode ?? data?.resultCode;
  if (code != null && !["00", "0000", "0"].includes(String(code))) throw new Error(`API 응답 오류 (${code})`);
  return data;
}

export const api = {
  hasLiveTourApi: Boolean(config.TOUR_API_KEY),
  hasKakaoMap: Boolean(config.KAKAO_JS_KEY),

  async searchTour({ keyword = "", areaCode = 39, contentTypeId = "" } = {}) {
    if (!config.TOUR_API_KEY) return null;
    const endpoint = keyword
      ? "https://apis.data.go.kr/B551011/KorService2/searchKeyword2"
      : "https://apis.data.go.kr/B551011/KorService2/areaBasedList2";
    const url = withQuery(endpoint, {
      serviceKey: config.TOUR_API_KEY,
      MobileOS: "ETC",
      MobileApp: "NeorangGaljido",
      _type: "json",
      numOfRows: 30,
      pageNo: 1,
      arrange: "Q",
      keyword,
      areaCode,
      contentTypeId,
    });
    const data = await requestJson(url);
    return data?.response?.body?.items?.item || [];
  },

  async searchNearby({ lng, lat, radius = 10000, contentTypeId = "" }) {
    if (!config.TOUR_API_KEY) return null;
    const url = withQuery("https://apis.data.go.kr/B551011/KorService2/locationBasedList2", {
      serviceKey: config.TOUR_API_KEY,
      MobileOS: "ETC",
      MobileApp: "NeorangGaljido",
      _type: "json",
      numOfRows: 100,
      pageNo: 1,
      arrange: "E",
      mapX: lng,
      mapY: lat,
      radius: Math.round(Math.max(1000, Math.min(20000, radius))),
      contentTypeId,
    });
    const data = await requestJson(url);
    return data?.response?.body?.items?.item || [];
  },

  async getTourDetail(contentId) {
    if (!config.TOUR_API_KEY || !contentId) return null;
    const data = await requestJson(withQuery('https://apis.data.go.kr/B551011/KorService2/detailCommon2', {serviceKey:config.TOUR_API_KEY, MobileOS:'ETC', MobileApp:'NeorangGaljido', _type:'json', contentId}));
    const items = data?.response?.body?.items?.item;
    return Array.isArray(items) ? items[0] : items;
  },

  async getWeather({ nx, ny, baseDate, baseTime }) {
    const cacheKey = `${nx}:${ny}:${baseDate}:${baseTime}`;
    if (weatherCache.has(cacheKey)) return weatherCache.get(cacheKey);
    const serviceKey = config.WEATHER_API_KEY || config.DATA_GO_KR_KEY;
    if (!serviceKey) return null;
    const url = withQuery("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst", {
      serviceKey,
      pageNo: 1,
      numOfRows: 1000,
      dataType: "JSON",
      base_date: baseDate,
      base_time: baseTime,
      nx,
      ny,
    });
    const data = await requestJson(url);
    const items = data?.response?.body?.items?.item || [];
    if (items.length) weatherCache.set(cacheKey, items);
    return items;
  },

  async getMidWeather({ landRegId = "11G00000", temperatureRegId = "11G00201", tmFc }) {
    const serviceKey = config.WEATHER_API_KEY || config.DATA_GO_KR_KEY;
    if (!serviceKey || !tmFc) return null;
    const common = { serviceKey, pageNo: 1, numOfRows: 10, dataType: "JSON", tmFc };
    try {
      const [landData, temperatureData] = await Promise.all([
        requestJson(withQuery("https://apis.data.go.kr/1360000/MidFcstInfoService/getMidLandFcst", { ...common, regId: landRegId })),
        requestJson(withQuery("https://apis.data.go.kr/1360000/MidFcstInfoService/getMidTa", { ...common, regId: temperatureRegId })),
      ]);
      const landItems = landData?.response?.body?.items?.item;
      const temperatureItems = temperatureData?.response?.body?.items?.item;
      const land = Array.isArray(landItems) ? landItems[0] : landItems;
      const temperature = Array.isArray(temperatureItems) ? temperatureItems[0] : temperatureItems;
      return land || temperature ? { land, temperature, tmFc } : null;
    } catch {
      return null;
    }
  },

  async getSunTimes({ lat = 33.45, lng = 126.57, date = "today" } = {}) {
    const url = withQuery("https://api.sunrise-sunset.org/v2", { lat, lng, date, tz: "Asia/Seoul" });
    try {
      return await requestJson(url);
    } catch {
      return null;
    }
  },

  async getEvChargers({ zcode, numOfRows = 9999, pageNo = 1 } = {}) {
    const serviceKey = config.EV_CHARGER_API_KEY || config.DATA_GO_KR_KEY;
    if (!serviceKey) return null;
    const url = withQuery("https://apis.data.go.kr/B552584/EvCharger/getChargerInfo", {
      serviceKey,
      pageNo,
      numOfRows,
      zcode,
      dataType: "JSON",
    });
    try {
      const data = await requestJson(url);
      return data?.items?.item || data?.response?.body?.items?.item || [];
    } catch {
      return null;
    }
  },

  // Region-scoped, bounded and coalesced. No nationwide download on a map toggle.
  async getRegionalEvChargers({ zcode, pageSize = 500, maxPages = 8, onProgress } = {}) {
    if (!/^\d{2}$/.test(String(zcode))) throw new Error('지역 코드가 필요합니다.');
    const serviceKey = config.EV_CHARGER_API_KEY || config.DATA_GO_KR_KEY;
    if (!serviceKey) throw new Error('충전소 API 키가 없습니다.');
    const key = `${zcode}:${pageSize}:${maxPages}`;
    const cached = chargerCache.get(key);
    if (cached && Date.now() - cached.updatedAt < 120000) return cached;
    if (chargerRequests.has(key)) return chargerRequests.get(key);
    const promise = (async () => {
      const items = [];
      const getPage = async pageNo => {
        const data = await requestJson(withQuery('https://apis.data.go.kr/B552584/EvCharger/getChargerInfo', {serviceKey, zcode, pageNo, numOfRows: pageSize, dataType: 'JSON'}));
        const body = data?.response?.body || data;
        const code = data?.response?.header?.resultCode ?? data?.resultCode;
        if (code && !['00','0000'].includes(String(code))) throw new Error('충전소 API 오류');
        const rows = body?.items?.item || [];
        return { items: Array.isArray(rows) ? rows : [rows], total: Number(body?.totalCount || rows.length) };
      };
      const first = await getPage(1); items.push(...first.items);
      onProgress?.({items:[...items], partial:items.length < first.total, updatedAt:Date.now()});
      const pages = Math.min(maxPages, Math.ceil(first.total / pageSize));
      // At most two simultaneous requests, bounded to prevent a burst on public APIs.
      for (let page = 2; page <= pages; page += 2) {
        const batch = await Promise.all([getPage(page), ...(page + 1 <= pages ? [getPage(page + 1)] : [])]);
        batch.forEach(result => items.push(...result.items));
        onProgress?.({items:[...items], partial:items.length < first.total, updatedAt:Date.now()});
      }
      const result = {items, partial: first.total > items.length, updatedAt: Date.now()};
      chargerCache.set(key, result);
      return result;
    })().finally(() => chargerRequests.delete(key));
    chargerRequests.set(key, promise);
    return promise;
  },

  async getCarDirections({ origin, destination, waypoints = [] }) {
    if (!config.KAKAO_REST_KEY || !origin || !destination) return null;
    const sameOriginProxy = location.protocol.startsWith("http") ? location.origin : "";
    const proxyBase = String(config.API_PROXY_URL || sameOriginProxy).replace(/\/$/, "");
    const endpoint = proxyBase ? `${proxyBase}/api/kakao-directions` : "https://apis-navi.kakaomobility.com/v1/directions";
    const url = withQuery(endpoint, {
      origin: `${origin.lng},${origin.lat}`,
      destination: `${destination.lng},${destination.lat}`,
      waypoints: waypoints.map((point) => `${point.lng},${point.lat}`).join("|"),
      priority: "RECOMMEND",
      car_fuel: "GASOLINE",
    });
    try {
      const data = await requestJson(url, { headers: { Authorization: `KakaoAK ${config.KAKAO_REST_KEY}` } });
      return data?.routes?.[0] || null;
    } catch {
      return null;
    }
  },

  loadKakaoMap() {
    if (!config.KAKAO_JS_KEY || window.kakao?.maps) return Promise.resolve(Boolean(window.kakao?.maps));
    return new Promise((resolve) => {
      const script = document.createElement("script");
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(config.KAKAO_JS_KEY)}&autoload=false&libraries=services,clusterer`;
      script.onload = () => window.kakao.maps.load(() => resolve(true));
      script.onerror = () => resolve(false);
      document.head.append(script);
    });
  },
};
