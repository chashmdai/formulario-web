import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePreferences } from './preferences';

function system(matches: Record<string, boolean>) {
  vi.stubGlobal('matchMedia', (media: string) => ({matches: !!matches[media], media, addEventListener: vi.fn(), removeEventListener: vi.fn()}));
}
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); delete document.documentElement.dataset.theme; });

describe('preferencias visuales', () => {
  it('usa el modo claro por defecto aunque el sistema esté en modo oscuro, sin guardar una elección', () => {
    system({'(prefers-color-scheme: dark)': true});
    const {result} = renderHook(() => usePreferences(false));
    expect(result.current.theme).toBe('light');expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem('siger:theme')).toBeNull();
  });
  it('respeta el modo oscuro solo cuando la persona lo eligió y guardó', () => {
    localStorage.setItem('siger:theme', 'dark');system({});
    expect(renderHook(() => usePreferences(false)).result.current.theme).toBe('dark');
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
