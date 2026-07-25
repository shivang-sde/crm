import type { NextConfig } from "next";

const backendProxyUrl =
  process.env.BACKEND_PROXY_URL ??
  "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",

  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendProxyUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;