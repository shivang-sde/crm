import { ResetPasswordForm } from "@/components/auth/ResetPasswordForm";

// Next.js App Router dynamic route page
export default async function ResetPasswordPage(
  props: { params: Promise<{ token: string }> }
) {
  const params = await props.params;
  return <ResetPasswordForm token={params.token} />;
}
