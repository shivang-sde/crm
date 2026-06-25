"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Eye, EyeOff, Lock, Mail, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Checkbox } from "@/components/ui/checkbox";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useMutation } from "@tanstack/react-query";
import { authApi } from "@/lib/api/auth";
import { useAuthStore } from "@/lib/store/authStore";
import { toast } from "sonner";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { getDashboardRoute } from "@/lib/constants/navigation";
import { LoginRequest } from "@/types/auth";

const formSchema = z.object({
  email: z.string().email({ message: "Please enter a valid email address." }),
  password: z.string().min(1, { message: "Password is required." }),
  rememberMe: z.boolean().optional(),
});

export function LoginForm() {
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [showPassword, setShowPassword] = useState(false);

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema) as any,
    defaultValues: {
      email: "",
      password: "",
      rememberMe: false,
    },
  });

  const mutation = useMutation({
    mutationFn: (data: LoginRequest) => authApi.login(data),
    onSuccess: (data) => {
      if (!data?.accessToken || !data?.user) {
        toast.error("Login response is invalid. Please try again.");
        return;
      }
      const role = data.user.roleName || data.user.role || "EMPLOYEE";
      setAuth(data.user, data.accessToken, role, data.tenant);
      
      // Personalized welcome message
      const userName = data.user.firstName || data.user.email;
      toast.success(`Welcome back, ${userName}!`);
      
      router.replace(getDashboardRoute(role));
    },
    onError: (error: unknown) => {
      const responseError = error as {
        response?: {
          data?: {
            error?: {
              message?: string;
            };
          };
        };
      };
      const message = responseError.response?.data?.error?.message || "Invalid credentials. Please try again.";
      toast.error(message);
    },
  });

  function onSubmit(values: z.infer<typeof formSchema>) {
    // Omit rememberMe if your LoginRequest type doesn't include it
    const { rememberMe, ...loginData } = values;
    mutation.mutate(loginData as LoginRequest);
  }

  return (
    <Card className="w-full shadow-xl border-0 sm:border sm:shadow-sm">
      <CardHeader className="space-y-1 pb-4 text-center sm:text-left">
        <CardTitle className="text-2xl font-bold tracking-tight">Welcome back</CardTitle>
        <CardDescription className="text-base">
          Enter your credentials to access your CRM dashboard.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5">
          <FieldGroup>
            {/* Email Field */}
            <Field data-invalid={!!form.formState.errors.email}>
              <FieldLabel htmlFor="email">Email Address</FieldLabel>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input 
                  id="email" 
                  type="email"
                  placeholder="name@company.com" 
                  className="pl-9"
                  aria-invalid={!!form.formState.errors.email} 
                  {...form.register("email")} 
                />
              </div>
              {form.formState.errors.email && (
                <FieldError>{form.formState.errors.email.message}</FieldError>
              )}
            </Field>

            {/* Password Field */}
            <Field data-invalid={!!form.formState.errors.password}>
              <div className="flex items-center justify-between">
                <FieldLabel htmlFor="password">Password</FieldLabel>
                <Link 
                  href="/forgot-password" 
                  className="text-xs font-medium text-primary hover:underline underline-offset-4"
                >
                  Forgot password?
                </Link>
              </div>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input 
                  id="password" 
                  type={showPassword ? "text" : "password"} 
                  placeholder="••••••••" 
                  className="pl-9 pr-9"
                  aria-invalid={!!form.formState.errors.password} 
                  {...form.register("password")} 
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {form.formState.errors.password && (
                <FieldError>{form.formState.errors.password.message}</FieldError>
              )}
            </Field>

            {/* Remember Me */}
            <div className="flex items-center gap-2">
              <Checkbox 
                id="rememberMe" 
                checked={form.watch("rememberMe")}
                onCheckedChange={(checked) => form.setValue("rememberMe", checked === true)}
              />
              <label
                htmlFor="rememberMe"
                className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 cursor-pointer"
              >
                Keep me signed in for 30 days
              </label>
            </div>
          </FieldGroup>

          {/* Submit Button */}
          <Button 
            type="submit" 
            className="w-full h-10 font-semibold shadow-md hover:shadow-lg transition-all group" 
            disabled={mutation.isPending}
          >
            {mutation.isPending ? (
              <>
                <Spinner data-icon="inline-start" className="mr-2" />
                Signing in...
              </>
            ) : (
              <>
                Sign In
                <ArrowRight className="ml-2 h-4 w-4 group-hover:translate-x-1 transition-transform" />
              </>
            )}
          </Button>

          {/* SSO Divider (Optional but recommended for Enterprise look) */}
          {/* <div className="relative my-2">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-card px-2 text-muted-foreground">Or continue with</span>
            </div>
          </div> */}

          {/* SSO Button (Placeholder) */}
          {/* <Button variant="outline" type="button" className="w-full h-10 font-medium">
            <svg className="mr-2 h-4 w-4" viewBox="0 0 24 24">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
            </svg>
            Sign in with SSO
          </Button> */}
        </form>
      </CardContent>
      <CardFooter className="flex flex-col gap-2 pt-4 border-t">
        <div className="text-sm text-muted-foreground text-center">
          Don&apos;t have an account?{" "}
          <Link href="/sign-up" className="font-semibold text-primary hover:underline underline-offset-4">
            Create free account
          </Link>
        </div>
      </CardFooter>
    </Card>
  );
}