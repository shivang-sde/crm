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

  async redirects() {
    return [
      {
        // Backward compat: old published URLs /forms/<publicKey> → canonical /forms/public/<publicKey>
        source: "/forms/:publicKey",
        destination: "/forms/public/:publicKey",
        permanent: false,
      },
    ];
  },

  async headers() {
    return [
      {
        // Public forms are intentionally frameable for embedding on external sites
        source: "/forms/public/:path*",
        headers: [
          { key: "X-Frame-Options", value: "ALLOWALL" },
          { key: "Content-Security-Policy", value: "frame-ancestors *" },
          { key: "X-Robots-Tag", value: "noindex, nofollow" },
        ],
      },
      {
        // All other routes remain protected from framing (clickjacking defense)
        source: "/:path*",
        headers: [
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          { key: "Content-Security-Policy", value: "frame-ancestors 'self'" },
        ],
      },
    ];
  },
};

export default nextConfig;