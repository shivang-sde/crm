import { NextRequest, NextResponse } from "next/server";
import { isPublicRoute } from "./lib/constants/navigation";

/**
 * Edge proxy responsibilities (deliberately narrow):
 *
 *   1. keep public authentication routes open
 *   2. require a structurally valid access token for everything else
 *   3. send "/" to the entry resolver (/home)
 *
 * The proxy does NOT perform permission-based route authorization:
 * the authoritative permission map lives in the application auth store
 * (loaded per user from the backend) and is not available — and must not be
 * duplicated — at the edge. Permission-aware navigation/routing is enforced
 * client-side by RouteGuard + getDefaultRoute(), and every underlying API
 * remains protected by backend RBAC (RbacFilter + method security), which is
 * the actual security boundary.
 */
function hasValidTokenShape(token: string): boolean {
  try {
    const payload = token.split(".")[1];
    if (!payload) return false;

    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);

    const json =
      typeof Buffer !== "undefined"
        ? Buffer.from(padded, "base64").toString("utf-8")
        : globalThis.atob(padded);

    const parsed = JSON.parse(json);
    return Boolean(parsed && (parsed.sub || parsed.user_id || parsed.userId));
  } catch {
    return false;
  }
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Ignore internal routes
  if (
    pathname.startsWith("/_next") ||
    pathname.startsWith("/api") ||
    pathname.startsWith("/static") ||
    pathname === "/favicon.ico"
  ) {
    return NextResponse.next();
  }

  // Root -> entry resolver (permission-driven redirect happens there)
  if (pathname === "/") {
    return NextResponse.redirect(new URL("/home", request.url));
  }

  // Public routes stay open
  if (isPublicRoute(pathname)) {
    return NextResponse.next();
  }

  // Authentication gate
  const accessToken = request.cookies.get("access_token")?.value;

  if (!accessToken || !hasValidTokenShape(accessToken)) {
    return NextResponse.redirect(new URL("/sign-in", request.url));
  }

  // Authenticated: allow through; permission-aware routing is handled by
  // RouteGuard in the app shell using the loaded permission map.
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next|static|favicon.ico).*)"],
};
