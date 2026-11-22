// Execute contra uma instalação local com o catálogo de demonstração.
const {chromium} = require("playwright");
const assert = require("node:assert/strict");

async function abrirMapa(browser, usuario) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto("http://localhost:5173");
    await page.getByRole("button", {name: "Entrar", exact: true}).click();
    await page.locator("#username").fill(usuario);
    await page.locator("#password").fill(process.env.PULSEPASS_DEMO_PASSWORD || "pulsepass-demo-local");
    await page.locator("#kc-login").click();
    await page.getByRole("button", {name: "Sair"}).waitFor();
    await page.getByRole("link", {name: "Festival Orla"}).click();
    await page.getByRole("link", {name: "Escolher assentos"}).click();
    await page.locator(".seat-list button").first().waitFor();
    return page;
}

async function testar() {
    const browser = await chromium.launch();
    try {
        const [cliente, observador] = await Promise.all([
            abrirMapa(browser, "cliente"), abrirMapa(browser, "organizador")
        ]);
        const livres = cliente.locator(".seat-list button[title=LIVRE]");
        assert.ok(await livres.count(), "O festival precisa de um lugar livre para este teste.");
        const lugar = await livres.first().innerText();
        const setor = await livres.first().locator("xpath=ancestor::section/h2").innerText();
        const mapa = observador.locator("section").filter({has: observador.getByRole("heading", {name: setor, exact: true})});
        await livres.first().click();
        await cliente.getByRole("button", {name: "Reservar (1/8)", exact: true}).click();
        await cliente.getByRole("heading", {name: "Meu pedido"}).waitFor();
        await mapa.getByRole("button", {name: lugar.trim() + " ▧", exact: true}).waitFor({timeout: 2000});
        await cliente.getByLabel("Cartão simulado").fill("4111111111111111");
        await cliente.getByRole("button", {name: "Confirmar pagamento"}).click();
        await cliente.getByRole("heading", {name: "Pagamento confirmado"}).waitFor();
        console.log("OK: compra no navegador e atualização do segundo mapa.");
    } finally {
        await browser.close();
    }
}
testar().catch(error => { console.error(error); process.exitCode = 1; });
