/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  // Exclut les tests Playwright (e2e/) — ils utilisent une API incompatible
  // avec Jest et doivent être lancés via `npm run test:e2e`.
  testPathIgnorePatterns: ['/node_modules/', '/dist/', '/e2e/'],
  moduleNameMapper: {
    '^@core/(.*)$': '<rootDir>/src/app/core/$1',
    '^@shared/(.*)$': '<rootDir>/src/app/shared/$1',
    '^@features/(.*)$': '<rootDir>/src/app/features/$1',
    '^@api/(.*)$': '<rootDir>/src/app/api/$1',
    '^@env/(.*)$': '<rootDir>/src/environments/$1'
  },
  collectCoverageFrom: [
    'src/app/**/*.ts',
    '!src/app/**/*.spec.ts',
    '!src/app/**/index.ts',
    '!src/app/**/*.module.ts'
  ],
  coverageDirectory: 'coverage',
  coverageReporters: ['html', 'text-summary', 'json-summary', 'lcov'],
  // Seuils ajustés au scope V1 : tests unitaires couvrent les facades (CRUD,
  // signals, error mapping) — pas les composants pages/dialogs.
  // À relever après ajout de tests composants en 10B/post-10A.
  coverageThreshold: {
    global: {
      branches: 12,
      functions: 25,
      lines: 20,
      statements: 20
    }
  }
};
