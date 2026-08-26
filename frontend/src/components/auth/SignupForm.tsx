"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useMutation } from "@tanstack/react-query";
import { authApi } from "@/lib/api/auth";
import { useAuthStore } from "@/lib/store/authStore";
import { toast } from "sonner";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { RegisterRequest } from "@/types/auth";

const formSchema = z.object({
  firstName: z.string().min(1, { message: "First name is required" }),
  lastName: z.string().min(1, { message: "Last name is required" }),
  companyName: z.string().min(1, { message: "Company name is required" }),
  email: z.string().email({ message: "Invalid email address" }),
  password: z.string().min(6, { message: "Password must be at least 6 characters" }),
  confirmPassword: z.string().min(6, { message: "Password must be at least 6 characters" })
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords do not match",
  path: ["confirmPassword"],
});

export function SignupForm() {
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema) as any,
    defaultValues: {
      firstName: "",
      lastName: "",
      companyName: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const mutation = useMutation({
    mutationFn: (data: RegisterRequest) => authApi.register(data),
    onSuccess: (data) => {
      if (!data?.accessToken || !data?.user) {
        toast.error("Registration response is invalid. Please try again.");
        return;
      }

      setAuth(data.user, data.accessToken, data.user.role || 'EMPLOYEE', data.tenant);
      toast.success("Account created successfully!");
      // Entry resolver performs the permission-driven default redirect.
      router.replace("/home");
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
      const message = responseError.response?.data?.error?.message || "Failed to create account. Please try again.";
      toast.error(message);
    },
  });

  function onSubmit(values: z.infer<typeof formSchema>) {
    const { confirmPassword, ...registerData } = values;
    mutation.mutate(registerData);
  }

  return (
    <Card className="w-full max-w-md mx-auto">
      <CardHeader className="space-y-1">
        <CardTitle className="text-2xl font-bold tracking-tight text-center">Create an account</CardTitle>
        <CardDescription className="text-center">
          Enter your details below to create your CRM account
        </CardDescription>
      </CardHeader>
      <CardContent>
          <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
            <FieldGroup>
              <div className="grid grid-cols-2 gap-4">
                <Field data-invalid={!!form.formState.errors.firstName}>
                  <FieldLabel htmlFor="firstName">First Name</FieldLabel>
                  <Input 
                    id="firstName" 
                    placeholder="John" 
                    aria-invalid={!!form.formState.errors.firstName} 
                    {...form.register("firstName")} 
                  />
                  {form.formState.errors.firstName && (
                    <FieldError>{form.formState.errors.firstName.message}</FieldError>
                  )}
                </Field>
                <Field data-invalid={!!form.formState.errors.lastName}>
                  <FieldLabel htmlFor="lastName">Last Name</FieldLabel>
                  <Input 
                    id="lastName" 
                    placeholder="Doe" 
                    aria-invalid={!!form.formState.errors.lastName} 
                    {...form.register("lastName")} 
                  />
                  {form.formState.errors.lastName && (
                    <FieldError>{form.formState.errors.lastName.message}</FieldError>
                  )}
                </Field>
              </div>
              <Field data-invalid={!!form.formState.errors.companyName}>
                <FieldLabel htmlFor="companyName">Company Name</FieldLabel>
                <Input 
                  id="companyName" 
                  placeholder="Acme Corp" 
                  aria-invalid={!!form.formState.errors.companyName} 
                  {...form.register("companyName")} 
                />
                {form.formState.errors.companyName && (
                  <FieldError>{form.formState.errors.companyName.message}</FieldError>
                )}
              </Field>
              <Field data-invalid={!!form.formState.errors.email}>
                <FieldLabel htmlFor="email">Email</FieldLabel>
                <Input 
                  id="email" 
                  placeholder="m@example.com" 
                  aria-invalid={!!form.formState.errors.email} 
                  {...form.register("email")} 
                />
                {form.formState.errors.email && (
                  <FieldError>{form.formState.errors.email.message}</FieldError>
                )}
              </Field>
              <Field data-invalid={!!form.formState.errors.password}>
                <FieldLabel htmlFor="password">Password</FieldLabel>
                <Input 
                  id="password" 
                  type="password" 
                  aria-invalid={!!form.formState.errors.password} 
                  {...form.register("password")} 
                />
                {form.formState.errors.password && (
                  <FieldError>{form.formState.errors.password.message}</FieldError>
                )}
              </Field>
              <Field data-invalid={!!form.formState.errors.confirmPassword}>
                <FieldLabel htmlFor="confirmPassword">Confirm Password</FieldLabel>
                <Input 
                  id="confirmPassword" 
                  type="password" 
                  aria-invalid={!!form.formState.errors.confirmPassword} 
                  {...form.register("confirmPassword")} 
                />
                {form.formState.errors.confirmPassword && (
                  <FieldError>{form.formState.errors.confirmPassword.message}</FieldError>
                )}
              </Field>
            </FieldGroup>
            <Button type="submit" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending && <Spinner data-icon="inline-start" />}
              {mutation.isPending ? "Creating account..." : "Sign Up"}
            </Button>
          </form>
      </CardContent>
      <CardFooter className="flex justify-center">
        <div className="text-sm text-gray-500">
          Already have an account?{" "}
          <Link href="/sign-in" className="font-semibold text-primary hover:underline">
            Sign in
          </Link>
        </div>
      </CardFooter>
    </Card>
  );
}
