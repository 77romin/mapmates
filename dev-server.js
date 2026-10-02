const http = require("http");
const fs = require("fs");
const path = require("path");

const root = __dirname;
const port = Number(process.env.PORT || 5500);
const mime = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
};

http.createServer(async (request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, `http://${request.headers.host}`).pathname);
  if (pathname === "/api/kakao-directions") {
    const incoming = new URL(request.url, `http://${request.headers.host}`);
    const target = new URL("https://apis-navi.kakaomobility.com/v1/directions");
    ["origin", "destination", "waypoints", "priority", "car_fuel"].forEach((key) => {
      if (incoming.searchParams.has(key)) target.searchParams.set(key, incoming.searchParams.get(key));
    });
    try {
      const apiResponse = await fetch(target, { headers: { Accept: "application/json", Authorization: request.headers.authorization || "" } });
      const body = await apiResponse.text();
      response.writeHead(apiResponse.status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
      response.end(body);
    } catch {
      response.writeHead(502, { "Content-Type": "application/json; charset=utf-8" });
      response.end(JSON.stringify({ error: "Kakao Mobility proxy failed" }));
    }
    return;
  }
  const relative = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  const file = path.resolve(root, relative);

  if (!file.startsWith(`${root}${path.sep}`) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Not found");
    return;
  }

  response.writeHead(200, {
    "Content-Type": mime[path.extname(file).toLowerCase()] || "application/octet-stream",
    "Cache-Control": "no-store",
  });
  fs.createReadStream(file).pipe(response);
}).listen(port, () => {
  console.log(`너랑 갈.지도: http://localhost:${port}`);
});
