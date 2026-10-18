import '@testing-library/jest-dom/vitest';

Object.defineProperty(window, 'crypto', {
  value: { randomUUID: () => 'test-uuid' },
  configurable: true
});
