import { test, expect } from '@playwright/test';

test('autorização, seleção, confirmação e trilha de auditoria',async({page})=>{
 const beneficiary='BROWSER-'+Date.now();
 await page.goto('/');await expect(page.getByRole('heading',{name:'Portabilidade, com clareza.'})).toBeVisible();
 await expect(page.getByRole('button',{name:'Autorizar e consultar'})).toBeDisabled();
 await page.getByLabel('Identificador do beneficiário').fill(beneficiary);
 await page.getByRole('checkbox',{name:/Autorizo a consulta/}).check();
 await page.getByRole('button',{name:'Autorizar e consultar'}).click();
 await expect(page.getByRole('table')).toBeVisible();
 await expect(page.locator('tbody tr')).toHaveCount(6);
 await page.locator('tbody input[type=checkbox]:enabled').first().check();
 await page.getByLabel('Código do banco').fill('001');await page.getByLabel('Agência',{exact:true}).fill('1234');await page.getByLabel('Conta com dígito').fill('12345-6');
 await page.getByRole('button',{name:'Solicitar portabilidade'}).click();
 await expect(page.locator('.operation-card.active')).toContainText(beneficiary);
 await expect(page.locator('.operation-card.active')).toContainText('Confirmada',{timeout:45000});
 await expect(page.locator('.timeline')).toContainText('Conta de recebimento atualizada');
 await expect(page.locator('tbody')).toContainText('Recebimento: 001 / 1234 / 12345-6');
 await page.evaluate(()=>window.scrollTo(0,0));
 await page.screenshot({path:'test-results/desktop-confirmada.png',fullPage:true});
 await page.setViewportSize({width:390,height:844});
 await expect(page.getByRole('heading',{name:'Portabilidade, com clareza.'})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
 await page.screenshot({path:'test-results/mobile-confirmada.png',fullPage:true});
});

test('rejeição exibe motivo e preserva conta de recebimento',async({page})=>{
 await page.goto('/');await page.getByLabel('Identificador do beneficiário').fill('REJECT-'+Date.now());
 await page.getByRole('checkbox',{name:/Autorizo a consulta/}).check();await page.getByRole('button',{name:'Autorizar e consultar'}).click();
 await page.locator('tbody input[type=checkbox]:enabled').first().check();
 await page.getByLabel('Código do banco').fill('001');await page.getByLabel('Agência',{exact:true}).fill('1234');await page.getByLabel('Conta com dígito').fill('12345-6');
 await page.getByText('Cenário da demonstração',{exact:true}).click();await page.getByLabel('Comportamento da Central').selectOption('REJECT');
 await page.getByRole('button',{name:'Solicitar portabilidade'}).click();await expect(page.locator('.operation-card.active')).toContainText('Rejeitada',{timeout:45000});
 await expect(page.locator('.event-panel .notice')).toContainText('política de portabilidade');
 await expect(page.locator('tbody')).not.toContainText('Recebimento:');
 await expect(page.locator('tbody input[type=checkbox]:enabled')).toHaveCount(4);
});
