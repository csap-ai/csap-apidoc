module.exports = {
  env: {
    browser: true,
    es2021: true,
    node: true,
  },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react/recommended',
    'plugin:react-hooks/recommended',
  ],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaFeatures: {
      jsx: true,
    },
    ecmaVersion: 'latest',
    sourceType: 'module',
    project: './tsconfig.json',
  },
  plugins: ['@typescript-eslint', 'react', 'react-refresh'],
  rules: {
    // React 规则
    'react/react-in-jsx-scope': 'off', // React 17+ 不需要导入 React
    'react/prop-types': 'off', // 使用 TypeScript，不需要 prop-types
    'react-refresh/only-export-components': 'warn',
    
    // TypeScript 规则
    '@typescript-eslint/no-unused-vars': ['warn', {
      argsIgnorePattern: '^_', // 忽略以 _ 开头的未使用变量
      varsIgnorePattern: '^_',
    }],
    '@typescript-eslint/no-explicit-any': 'warn', // any 类型警告（可逐步优化）
    '@typescript-eslint/explicit-module-boundary-types': 'off', // 允许隐式返回类型
    
    // 代码质量规则
    'prefer-const': 'error', // 强制使用 const
    'no-console': 'off', // 开发工具项目，允许使用 console
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    
    // React Hooks 规则
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn', // Hook 依赖检查
  },
  settings: {
    react: {
      version: 'detect',
    },
  },
  ignorePatterns: [
    'dist',
    'node_modules',
    'build',
    '*.config.js',
    '*.config.ts',
    'mock',
    'public',
  ],
}
