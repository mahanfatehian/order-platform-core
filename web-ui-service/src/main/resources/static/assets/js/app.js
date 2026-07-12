document.addEventListener('htmx:configRequest', function (event) {
  const token = document.querySelector('meta[name="_csrf"]');
  const header = document.querySelector('meta[name="_csrf_header"]');
  if (token && header) event.detail.headers[header.content] = token.content;
});

document.addEventListener('htmx:responseError', function () {
  const region = document.querySelector('[data-service-feedback]');
  if (region) region.textContent = 'This section could not be refreshed. Use the page reload button to try again.';
});
