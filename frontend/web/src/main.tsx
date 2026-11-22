import {useEffect, useState} from "react";
import {createRoot} from "react-dom/client";
import "./style.css";

type Summary = {id: string; nome: string; local: string; dataHora: string; precoMinimo: number; disponibilidade: string};
type Sector = {id: string; nome: string; preco: number; livres: number; capacidade: number; acessivel: boolean};
type Detail = Summary & {descricao: string; setores: Sector[]};
type Page = {itens: Summary[]; total: number};
const money = (cents: number) => (cents / 100).toLocaleString("pt-BR", {style: "currency", currency: "BRL"});
const date = (utc: string) => new Date(utc).toLocaleString("pt-BR", {dateStyle: "long", timeStyle: "short"});

function useResource<T>(url: string) {
    const [result, setResult] = useState<{url: string; value?: T; error?: string}>();
    useEffect(() => {
        const cancel = new AbortController();
        fetch(url, {signal: cancel.signal})
            .then(async response => {
                if (!response.ok) throw new Error(response.status === 404 ? "Evento não encontrado." : "Falha ao consultar o catálogo.");
                return await response.json() as T;
            })
            .then(value => setResult({url, value}))
            .catch(() => {
                if (!cancel.signal.aborted) setResult({url, error: "Não foi possível carregar os eventos."});
            });
        return () => cancel.abort();
    }, [url]);
    return result?.url === url ? result : undefined;
}

function Catalog() {
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(0);
    const result = useResource<Page>("/api/inventory/events?" + new URLSearchParams({q: query, page: String(page), size: "20"}));
    return <><h1>Eventos</h1><label>Buscar por nome<input type="search" value={query} onChange={e => {setQuery(e.target.value); setPage(0);}} /></label>
        {!result && <p role="status">Carregando eventos…</p>}
        {result?.error && <p role="alert">{result.error}</p>}
        {result?.value && <><p>{result.value.total} evento(s) encontrado(s)</p><div className="grid">{result.value.itens.map(event =>
            <article key={event.id}><p>{date(event.dataHora)}</p><h2><a href={"#evento/" + event.id}>{event.nome}</a></h2><p>{event.local}</p>
            <p>{event.disponibilidade === "ABERTA" ? "Vendas abertas" : "Esgotado"} · A partir de {money(event.precoMinimo)}</p></article>)}</div>
            {result.value.total > 20 && <nav aria-label="Paginação"><button disabled={!page} onClick={() => setPage(page-1)}>Anterior</button>
            <button disabled={(page+1)*20 >= result.value.total} onClick={() => setPage(page+1)}>Próxima</button></nav>}</>}
    </>;
}

function EventDetail({id}: {id: string}) {
    const result = useResource<Detail>("/api/inventory/events/" + encodeURIComponent(id));
    const event = result?.value;
    return <><a href="#">Voltar ao catálogo</a>{!result && <p role="status">Carregando evento…</p>}
    {result?.error && <p role="alert">{result.error}</p>}{event && <><h1>{event.nome}</h1><p>{event.descricao}</p>
    <p>{date(event.dataHora)} · {event.local}</p><h2>Setores</h2><div className="grid">{event.setores.map(sector =>
        <article key={sector.id}><h3>{sector.nome}</h3><p>{money(sector.preco)} · {sector.livres} de {sector.capacidade} livres</p>
        {sector.acessivel && <p>Acessível</p>}</article>)}</div></>}</>;
}

function App() {
    const [route, setRoute] = useState(location.hash);
    useEffect(() => {
        const change = () => setRoute(location.hash);
        window.addEventListener("hashchange", change);
        return () => window.removeEventListener("hashchange", change);
    }, []);
    return <><header><a href="#">PulsePass</a></header><main>{route.startsWith("#evento/") ? <EventDetail id={route.slice(8)}/> : <Catalog/>}</main></>;
}

createRoot(document.getElementById("root")!).render(<App/>);
