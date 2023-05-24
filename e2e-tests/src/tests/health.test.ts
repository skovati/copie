import { expect, test } from '@playwright/test';
import req from '../utilities/requests.js';

test.describe('Health', () => {
  test('Gateway is healthy', async ({ request }) => {
    const healthy = await req.healthGateway(request);
    expect(healthy).toBeTruthy();
  });

  test('Hasura is healthy', async ({ request }) => {
    const healthy = await req.healthHasura(request);
    expect(healthy).toBeTruthy();
  });

  test('UI is healthy', async ({ request }) => {
    const healthy = await req.healthUI(request);
    expect(healthy).toBeTruthy();
  });

  test('Merlin is healthy', async ({ request }) => {
    const healthy = await req.healthMerlin(request);
    expect(healthy).toBeTruthy();
  });

  test('Scheduler is healthy', async ({ request }) => {
    const healthy = await req.healthScheduler(request);
    expect(healthy).toBeTruthy();
  });

  test('Sequencing is healthy', async ({ request }) => {
    const healthy = await req.healthSequencing(request);
    expect(healthy).toBeTruthy();
  });

  test('Worker is healthy', async ({ request }) => {
    const healthy = await req.healthWorker(request);
    expect(healthy).toBeTruthy();
  });
});
