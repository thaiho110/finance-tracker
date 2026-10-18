import { describe, expect, it } from 'vitest';
import { isValidIsoDate, passwordProblems } from './index';

describe('passwordProblems', () => {
  it('accepts what the backend accepts', () => {
    expect(passwordProblems('MySecureP@ss123')).toEqual([]);
  });

  it('names what is missing', () => {
    expect(passwordProblems('short')).toEqual(['at least 8 characters', 'an uppercase letter', 'a digit', 'a symbol']);
  });
});

describe('isValidIsoDate', () => {
  it('rejects impossible dates', () => {
    expect(isValidIsoDate('2026-02-31')).toBe(false);
    expect(isValidIsoDate('2026-09-05')).toBe(true);
  });
});
