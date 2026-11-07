import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePreferences } from './preferences';

function system(matches: Record<string, boolean>) {
  vi.stubGlobal('matchMedia', (media: string) => ({matches: !!matches[media], media, addEventListener: vi.fn(), removeEventListener: vi.fn()}));
}
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); delete document.documentElement.dataset.theme; });

describe('preferencias visuales', () => {
  it('sigue el modo oscuro del sistema sin guardar una elección que la persona no hizo', () => {
    system({'(prefers-color-scheme: dark)': true});
    const {result} = renderHook(() => usePreferences(false));
    expect(result.current.theme).toBe('dark');expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('siger:theme')).toBeNull();
  });
  it('prioriza alto contraste del sistema y respeta una elección explícita posterior', () => {
    system({'(prefers-contrast: more)': true, '(prefers-color-scheme: dark)': true});
    const {result} = renderHook(() => usePreferences(false));
    expect(result.current.theme).toBe('contrast');
    act(() => result.current.onTheme('light'));
    expect(result.current.theme).toBe('light');expect(localStorage.getItem('siger:theme')).toBe('light');
  });
  it('una elección guardada prevalece sobre el sistema', () => {
    localStorage.setItem('siger:theme', 'contrast');system({'(prefers-color-scheme: dark)': true});
    expect(renderHook(() => usePreferences(false)).result.current.theme).toBe('contrast');
  });
});
