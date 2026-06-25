import { LoginForm } from "@/components/auth/LoginForm"; // Adjust path to your LoginForm
import Link from "next/link";
import { Zap, Users, BarChart3 } from "lucide-react";

export default function LoginPage() {
  return (
    <div className="min-h-screen grid grid-cols-1 lg:grid-cols-2 bg-background">
      {/* Left Side - Branding / Hero (Hidden on mobile) */}
      <div className="relative hidden lg:flex flex-col justify-between bg-slate-900 p-10 text-white overflow-hidden">
        {/* Background Gradient */}
        <div className="absolute inset-0 bg-gradient-to-br from-primary/30 via-transparent to-transparent" />
        
        {/* Content */}
        <div className="relative z-10">
          <Link href="/" className="flex items-center gap-2 font-bold text-2xl">
            <div className="h-9 w-9 rounded-lg bg-primary flex items-center justify-center shadow-lg">
               <span className="text-white text-lg">S</span> {/* Replace with your logo */}
            </div>
            <span>Sellspark CRM</span> {/* Replace with your brand name */}
          </Link>
        </div>

        <div className="relative z-10 max-w-lg">
          <h1 className="text-4xl font-bold leading-tight tracking-tight mb-6">
            Manage your customers, <br/> close deals faster.
          </h1>
          <p className="text-lg text-slate-300 mb-8 leading-relaxed">
            The all-in-one platform to track leads, automate workflows, and scale your business with data-driven insights.
          </p>
          
          <div className="space-y-5">
             <FeatureItem icon={Zap} text="Automate your sales pipeline with smart workflows." />
             <FeatureItem icon={Users} text="Collaborate with your team and track every interaction." />
             <FeatureItem icon={BarChart3} text="Actionable insights and customizable dashboards." />
          </div>
        </div>

        <div className="relative z-10 text-sm text-slate-400">
          © 2026 YourCRM. All rights reserved.
        </div>
      </div>

      {/* Right Side - Login Form */}
      <div className="flex items-center justify-center p-6 sm:p-10 bg-muted/30">
        <div className="w-full max-w-md">
           {/* Mobile Logo (Shown only on small screens) */}
           <div className="lg:hidden flex justify-center mb-8">
               <Link href="/" className="flex items-center gap-2 font-bold text-2xl">
                  <div className="h-9 w-9 rounded-lg bg-primary flex items-center justify-center text-white shadow-lg">C</div>
                  <span>YourCRM</span>
               </Link>
           </div>
           
           <LoginForm />
        </div>
      </div>
    </div>
  );
}

function FeatureItem({ icon: Icon, text }: { icon: React.ElementType, text: string }) {
   return (
     <div className="flex items-start gap-4">
        <div className="h-10 w-10 rounded-full bg-white/10 backdrop-blur-sm flex items-center justify-center flex-shrink-0 border border-white/20">
           <Icon className="h-5 w-5 text-primary" />
        </div>
        <p className="text-slate-200 pt-2 text-base">{text}</p>
     </div>
   )
}