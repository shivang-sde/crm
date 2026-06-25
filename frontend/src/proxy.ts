import { NextRequest, NextResponse } from "next/server";
import {
  getDashboardRoute,
  isPublicRoute,
  isRouteAllowedForRole,
} from "./lib/constants/navigation";

function decodeJwtRole(token: string): string | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;

    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);

    const json =
      typeof Buffer !== "undefined"
        ? Buffer.from(padded, "base64").toString("utf-8")
        : globalThis.atob(padded);

    const parsed = JSON.parse(json);

    return parsed?.role || parsed?.user?.role || null;
  } catch {
    return null;
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

  // Public routes
  if (isPublicRoute(pathname)) {
    return NextResponse.next();
  }

  // Access token
  const accessToken = request.cookies.get("access_token")?.value

  // Not authenticated
  if (!accessToken) {
    return NextResponse.redirect(
      new URL("/sign-in", request.url)
    );
  }

  // Decode role
  const role = decodeJwtRole(accessToken);

  // Invalid token
  if (!role) {
    return NextResponse.redirect(
      new URL("/sign-in", request.url)
    );
  }

  // Root redirect
  if (pathname === "/" || pathname === "/home") {
    return NextResponse.redirect(
      new URL(getDashboardRoute(role), request.url)
    );
  }

  // RBAC route check
  if (!isRouteAllowedForRole(pathname, role)) {
    return NextResponse.redirect(
      new URL(getDashboardRoute(role), request.url)
    );
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next|static|favicon.ico).*)"],
};