# Деплой web-проекта в Netlify

Проект готовится к деплою как статический Vite-сайт из папки `web`.

## Что уже настроено

- `netlify.toml` в корне репозитория указывает Netlify:
  - base directory: `web`
  - build command: `npm run build`
  - publish directory: `web/dist`
  - Node.js: `20`
- `.gitignore` исключает `web/node_modules/` и `web/dist/`.

## Проверка перед деплоем

Из корня проекта:

```bash
cd web
npm ci
npm run test
npm run build
```

После сборки готовые файлы будут в `web/dist`.

## Вариант 1: деплой через GitHub

1. Создай репозиторий на GitHub.
2. Загрузи туда проект:

```bash
git add .
git commit -m "Prepare web app for Netlify"
git branch -M main
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

3. Открой Netlify: https://app.netlify.com/
4. Нажми `Add new site` -> `Import an existing project`.
5. Выбери GitHub и нужный репозиторий.
6. Netlify прочитает `netlify.toml` автоматически. Настройки должны быть:
   - Base directory: `web`
   - Build command: `npm run build`
   - Publish directory: `web/dist`
7. Нажми `Deploy site`.

После каждого `git push` в `main` Netlify будет пересобирать сайт автоматически.

## Вариант 2: ручной деплой без GitHub

1. Собери проект:

```bash
cd web
npm ci
npm run build
```

2. Открой https://app.netlify.com/drop
3. Перетащи папку `web/dist` в окно браузера.

Этот способ быстрый, но автоматического обновления после изменений не будет.

## Если Netlify спросит настройки вручную

Используй такие значения:

```text
Base directory: web
Build command: npm run build
Publish directory: web/dist
Node version: 20
```
