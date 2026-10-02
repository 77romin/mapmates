const config = window.APP_CONFIG || {};

function withQuery(base, params) {
  const url = new URL(base);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, value);
  });
  return url.toString();
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, { ...options, headers: { Accept: "application/json", ...(options.headers || {}) } });
  if (!response.ok) throw new Error(`API 요청 실패 (${response.status})`);
  return response.json();
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

  async getWeather({ nx, ny, baseDate, baseTime }) {
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
    return data?.response?.body?.items?.item || [];
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
    const url = withQuery("https://api.sunrise-sunset.org/v2", { lat, lng, date, timezone: "Asia/Seoul" });
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

  async getAllEvChargers({ pageSize = 9999 } = {}) {
    const serviceKey = config.EV_CHARGER_API_KEY || config.DATA_GO_KR_KEY;
    if (!serviceKey) return null;
    const allChargers = [];
    let pageNo = 1;
    let totalCount = Infinity;
    while (allChargers.length < totalCount && pageNo <= 20) {
      const url = withQuery("https://apis.data.go.kr/B552584/EvCharger/getChargerInfo", {
        serviceKey,
        pageNo,
        numOfRows: pageSize,
        dataType: "JSON",
      });
      const data = await requestJson(url);
      const body = data?.response?.body || data;
      const items = body?.items?.item || data?.items?.item || [];
      totalCount = Number(body?.totalCount ?? data?.totalCount ?? items.length);
      allChargers.push(...items);
      if (!items.length || allChargers.length >= totalCount) break;
      pageNo += 1;
    }
    return allChargers;
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
