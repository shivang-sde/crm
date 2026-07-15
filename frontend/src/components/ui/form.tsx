"use client";

import * as React from "react";
import {
  Controller,
  FormProvider,
  useFormContext,
  type ControllerProps,
  type FieldPath,
  type FieldValues,
} from "react-hook-form";
import { cn } from "@/lib/utils";
import { Label } from "@/components/ui/label";

type FormFieldContextValue<
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
> = {
  name: TName;
  formItemId: string;
  formDescriptionId: string;
  formMessageId: string;
  error?: { message?: string };
};

const FormFieldContext = React.createContext<FormFieldContextValue>({} as FormFieldContextValue);

const Form = FormProvider;

function FormField<
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
>({ ...props }: ControllerProps<TFieldValues, TName>) {
  const formItemId = React.useId();
  const formDescriptionId = React.useId();
  const formMessageId = React.useId();

  return (
    <Controller
      {...props}
      render={(fieldProps) => {
        const contextValue: FormFieldContextValue<TFieldValues, TName> = {
          name: fieldProps.field.name as TName,
          formItemId,
          formDescriptionId,
          formMessageId,
          error: fieldProps.fieldState.error,
        };

        return (
          <FormFieldContext.Provider value={contextValue as FormFieldContextValue}>
            {props.render(fieldProps)}
          </FormFieldContext.Provider>
        );
      }}
    />
  );
}

function useFormField() {
  const context = React.useContext(FormFieldContext);

  if (!context) {
    throw new Error("useFormField should be used within <FormField>");
  }

  return context;
}

function FormItem({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="form-item" className={cn("space-y-2", className)} {...props} />;
}

function FormLabel({ className, ...props }: React.ComponentProps<typeof Label>) {
  const { error, formItemId } = useFormField();

  return (
    <Label
      data-slot="form-label"
      data-error={!!error}
      className={cn("data-[error=true]:text-destructive", className)}
      htmlFor={formItemId}
      {...props}
    />
  );
}

function FormControl({ children, ...props }: React.ComponentProps<"div"> & { children?: React.ReactNode }) {
  const { error, formItemId, formDescriptionId, formMessageId } = useFormField();

  if (!React.isValidElement(children)) {
    return null;
  }

  return React.cloneElement(children, {
    id: formItemId,
    "aria-invalid": !!error,
    "aria-describedby": error ? formMessageId : formDescriptionId,
    ...props,
  });
}

function FormDescription({ className, ...props }: React.ComponentProps<"p">) {
  const { formDescriptionId } = useFormField();

  return <p id={formDescriptionId} className={cn("text-sm text-muted-foreground", className)} {...props} />;
}

function FormMessage({ className, ...props }: React.ComponentProps<"p">) {
  const { error, formMessageId } = useFormField();
  const body = error?.message;

  if (!body) {
    return null;
  }

  return (
    <p id={formMessageId} className={cn("text-sm font-medium text-destructive", className)} {...props}>
      {body}
    </p>
  );
}

export {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  useFormField,
  useFormContext,
};

export type { FormFieldContextValue };
export default Form;
