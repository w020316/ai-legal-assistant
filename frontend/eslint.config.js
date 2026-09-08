// ESLint 扁平配置（ESLint 9）— v1.13.0 修复：此前缺失 eslint.config.js 导致 `npm run lint` 无法运行。
// 采用保守的"仅告警"规则集，聚焦真正会引发 Bug 的问题，不因风格噪声阻断。
import vueParser from 'vue-eslint-parser'
import tsParser from '@typescript-eslint/parser'
import tsPlugin from '@typescript-eslint/eslint-plugin'
import vuePlugin from 'eslint-plugin-vue'

export default [
  {
    ignores: ['dist/**', 'node_modules/**', '*.config.ts', '*.config.js'],
  },
  {
    files: ['**/*.{js,mjs,ts,vue}'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tsParser,
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
        ecmaFeatures: { jsx: false },
      },
    },
    plugins: {
      vue: vuePlugin,
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      // 未使用变量/导入 → 告警（下划线前缀可忽略，如 _env）
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // console 在生产保留无害，本项目无 logger 抽象，仅告警
      'no-console': 'warn',
      // 明显漏判空（可选）——保持最小侵入
    },
  },
]