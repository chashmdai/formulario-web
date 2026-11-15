import { afterEach, beforeEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import { forgetCsrf } from '../api';
// Cada prueba parte sin token CSRF en memoria, como una página recién abierta.
beforeEach(() => forgetCsrf());
afterEach(cleanup);
