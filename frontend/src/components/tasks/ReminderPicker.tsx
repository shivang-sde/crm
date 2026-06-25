import React from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { format } from 'date-fns';

interface ReminderPickerProps {
  value?: string;
  onChange: (value?: string) => void;
}

export function ReminderPicker({ value, onChange }: ReminderPickerProps) {
  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const dateValue = e.target.value;
    if (value) {
      // Keep the time portion if it exists
      const timePart = value.includes('T') ? value.split('T')[1] : '09:00';
      onChange(`${dateValue}T${timePart}`);
    } else {
      onChange(`${dateValue}T09:00`);
    }
  };

  const handleTimeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const timeValue = e.target.value;
    if (value) {
      const datePart = value.split('T')[0];
      onChange(`${datePart}T${timeValue}`);
    }
  };

  return (
    <div className="space-y-4 p-4 border rounded-lg">
      <div>
        <Label>Set Reminder</Label>
        <div className="flex gap-4 items-end">
          <div className="flex-1">
            <Label className="text-xs text-muted-foreground">Date</Label>
            <Input
              type="date"
              value={value ? value.split('T')[0] : ''}
              onChange={handleDateChange}
            />
          </div>
          <div className="flex-1">
            <Label className="text-xs text-muted-foreground">Time</Label>
            <Input
              type="time"
              value={value && value.includes('T') ? value.split('T')[1].substring(0, 5) : '09:00'}
              onChange={handleTimeChange}
            />
          </div>
        </div>
        {value && (
          <p className="text-sm text-muted-foreground mt-2">
            Reminder will be set for {format(new Date(value), 'MMM dd, yyyy HH:mm')}
          </p>
        )}
      </div>
    </div>
  );
}
