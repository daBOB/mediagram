import js from "@eslint/js";
import globals from "globals";

export default [
  { ignores: [".desloppify/**", ".claude/**"] },
  {
    ...js.configs.recommended,
    files: ["public/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: globals.browser,
    },
  },
];
