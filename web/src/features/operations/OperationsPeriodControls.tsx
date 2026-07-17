import type { FormEvent } from 'react';
import type { DashboardPeriodInput, DashboardPeriodPreset } from './storeOperationsDashboardApi';

type OperationsPeriodControlsProps = {
  period: DashboardPeriodInput;
  customFrom: string;
  customTo: string;
  validationMessage: string | null;
  onPresetChange: (preset: DashboardPeriodPreset) => void;
  onCustomFromChange: (value: string) => void;
  onCustomToChange: (value: string) => void;
  onCustomApply: () => void;
};

const presets: { value: DashboardPeriodPreset; label: string }[] = [
  { value: 'TODAY', label: '오늘' },
  { value: 'LAST_7_DAYS', label: '최근 7일' },
  { value: 'LAST_30_DAYS', label: '최근 30일' },
  { value: 'LAST_90_DAYS', label: '최근 90일' },
];

export function OperationsPeriodControls({
  period,
  customFrom,
  customTo,
  validationMessage,
  onPresetChange,
  onCustomFromChange,
  onCustomToChange,
  onCustomApply,
}: OperationsPeriodControlsProps) {
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onCustomApply();
  };

  return (
    <section className="operations-period-controls" aria-labelledby="operations-period-title">
      <div>
        <h2 id="operations-period-title">조회 기간</h2>
        <p>KST 기준 · 사용자 지정은 최대 90일</p>
      </div>
      <div className="operations-preset-buttons" aria-label="조회 기간 빠른 선택">
        {presets.map((preset) => (
          <button
            type="button"
            key={preset.value}
            aria-pressed={period.preset === preset.value}
            onClick={() => onPresetChange(preset.value)}
          >
            {preset.label}
          </button>
        ))}
      </div>
      <form className="operations-custom-period" onSubmit={submit}>
        <label>
          시작일 (KST)
          <input type="date" value={customFrom} onChange={(event) => onCustomFromChange(event.target.value)} />
        </label>
        <label>
          종료일 (KST)
          <input type="date" value={customTo} onChange={(event) => onCustomToChange(event.target.value)} />
        </label>
        <button className="secondary-button" type="submit">직접 설정 적용</button>
      </form>
      {validationMessage ? <p className="error-text" role="alert">{validationMessage}</p> : null}
    </section>
  );
}
