import React from 'react';
import { Recurrence } from '@/types/recurrence';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

interface RecurrencePickerProps {
  value?: Recurrence;
  onChange: (value?: Recurrence) => void;
}

export function RecurrencePicker({ value, onChange }: RecurrencePickerProps) {
  const [repeatType, setRepeatType] = React.useState(value?.repeatType || 'NEVER');

  const handleRepeatTypeChange = (type: string) => {
    setRepeatType(type);
    if (type === 'NEVER') {
      onChange(undefined);
    } else {
      onChange({
        repeatType: type as any,
        interval: 1,
        endType: 'NEVER',
      });
    }
  };

  const handleIntervalChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const interval = parseInt(e.target.value) || 1;
    onChange(value ? { ...value, interval } : undefined);
  };

  const handleEndTypeChange = (endType: string) => {
    onChange(value ? { ...value, endType: endType as any } : undefined);
  };

  const handleEndAfterCountChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const count = parseInt(e.target.value) || 0;
    onChange(value ? { ...value, endAfterCount: count } : undefined);
  };

  const handleEndDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const endDate = e.target.value;
    onChange(value ? { ...value, endDate } : undefined);
  };

  return (
    <div className="space-y-4 p-4 border rounded-lg">
      <div>
        <Label>Recurrence Pattern</Label>
        <Select value={repeatType} onValueChange={handleRepeatTypeChange}>
          <SelectTrigger>
            <SelectValue placeholder="Select recurrence" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="NEVER">Does not repeat</SelectItem>
            <SelectItem value="DAILY">Daily</SelectItem>
            <SelectItem value="WEEKLY">Weekly</SelectItem>
            <SelectItem value="MONTHLY">Monthly</SelectItem>
            <SelectItem value="YEARLY">Yearly</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {repeatType !== 'NEVER' && (
        <>
          <div>
            <Label>Repeat every</Label>
            <div className="flex gap-2 items-center">
              <Input
                type="number"
                min="1"
                value={value?.interval || 1}
                onChange={handleIntervalChange}
                className="w-20"
              />
              <span>
                {repeatType === 'DAILY' && 'day(s)'}
                {repeatType === 'WEEKLY' && 'week(s)'}
                {repeatType === 'MONTHLY' && 'month(s)'}
                {repeatType === 'YEARLY' && 'year(s)'}
              </span>
            </div>
          </div>

          <div>
            <Label>Ends</Label>
            <Select
              value={value?.endType || 'NEVER'}
              onValueChange={handleEndTypeChange}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select end type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NEVER">Never</SelectItem>
                <SelectItem value="AFTER_N_TIMES">After</SelectItem>
                <SelectItem value="ON_DATE">On date</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {value?.endType === 'AFTER_N_TIMES' && (
            <div>
              <Label>Occurrences</Label>
              <Input
                type="number"
                min="1"
                value={value?.endAfterCount || 0}
                onChange={handleEndAfterCountChange}
                className="w-20"
              />
            </div>
          )}

          {value?.endType === 'ON_DATE' && (
            <div>
              <Label>End Date</Label>
              <Input
                type="date"
                value={value?.endDate ? value.endDate.split('T')[0] : ''}
                onChange={handleEndDateChange}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
