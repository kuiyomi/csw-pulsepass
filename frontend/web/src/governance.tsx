import {useEffect,useState} from "react";
import type {FormEvent} from "react";
import {api} from "./session";

type RequestRow={id:string;nomeFantasia:string;documento:string;justificativa:string};
type EventRow={id:string;nome:string;status:string;vendidos:number;reservados:number};
const reason=(e:unknown)=>e instanceof Error?e.message:"Falha na operação.";
export function Apply(){
    const [feedback,set]=useState(""),[busy,working]=useState(false);
    async function submit(e:FormEvent<HTMLFormElement>){
        e.preventDefault();const form=new FormData(e.currentTarget);working(true);
        try{await api("/api/iam/organizer-requests","POST",Object.fromEntries(form));set("Solicitação enviada para aprovação.");}
        catch(ex){set(reason(ex));}finally{working(false);}
    }
    return <><h1>Solicitar perfil de organizador</h1><form onSubmit={submit}>
    <label>Nome fantasia<input name="nomeFantasia" minLength={3} maxLength={80} required/></label>
    <label>Documento<input name="documento" minLength={11} maxLength={18} required/></label>
    <label>Justificativa<textarea name="justificativa" maxLength={500} required/></label>
    <button disabled={busy}>Enviar solicitação</button></form><p role="status">{feedback}</p></>;
}
export function Admin(){
    const [rows,set]=useState<RequestRow[]>([]),[feedback,msg]=useState(""),[busy,working]=useState(false);
    const [page,paginate]=useState(0),[total,count]=useState(0);
    async function load(){
        const r=await api<{itens:RequestRow[];total:number}>("/api/iam/organizer-requests?page="+page);
        set(r.itens);count(r.total);
    }
    useEffect(()=>{load().catch(e=>msg(reason(e)));},[page]);
    async function decide(id:string,approve:boolean,motivo:string){
        working(true);
        try{await api("/api/iam/organizer-requests/"+id+(approve?"/approve":"/reject"),"POST",approve?undefined:{motivo});
            await load();msg(approve?"Solicitação aprovada.":"Solicitação rejeitada.");}
        catch(e){msg(reason(e));}finally{working(false);}
    }
    return <><h1>Solicitações de organizadores</h1>{!rows.length&&<p>Nenhuma solicitação pendente.</p>}
    {rows.map(r=><article key={r.id}><h2>{r.nomeFantasia}</h2><p>{r.documento} · {r.justificativa}</p>
    <button disabled={busy} onClick={()=>decide(r.id,true,"")}>Aprovar</button>
    <form onSubmit={e=>{e.preventDefault();void decide(r.id,false,String(new FormData(e.currentTarget).get("motivo")));}}>
    <label>Motivo da rejeição<input name="motivo" minLength={5} maxLength={300} required/></label>
    <button disabled={busy}>Rejeitar</button></form></article>)}
    {total>20&&<nav><button disabled={!page} onClick={()=>paginate(page-1)}>Anterior</button>
    <button disabled={(page+1)*20>=total} onClick={()=>paginate(page+1)}>Próxima</button></nav>}
    <p role="status">{feedback}</p></>;
}
export function Organizer(){
    const [rows,set]=useState<EventRow[]>([]),[feedback,msg]=useState(""),[editing,edit]=useState(""),[busy,working]=useState(false);
    async function load(){set(await api<EventRow[]>("/api/inventory/organizer/events"));}
    useEffect(()=>{load().catch(e=>msg(reason(e)));},[]);
    async function submit(e:FormEvent<HTMLFormElement>,kind:"event"|"sector"){
        e.preventDefault();const element=e.currentTarget,form=new FormData(element);working(true);
        try{
            if(kind==="event"){
                const r=await api<{id:string}>("/api/inventory/events","POST",{nome:form.get("nome"),local:form.get("local"),descricao:form.get("descricao"),
                    dataHora:new Date(String(form.get("quando"))).toISOString()});edit(r.id);msg("Rascunho criado. Adicione os setores.");
            }else{
                const r=await api<{assentosCriados:number}>("/api/inventory/events/"+editing+"/sectors","POST",{nome:form.get("nome"),
                    preco:Math.round(Number(form.get("preco"))*100),fileiras:Number(form.get("fileiras")),
                    assentosPorFileira:Number(form.get("lugares")),acessivel:form.has("acessivel")});msg(r.assentosCriados+" assentos criados.");
            }
            element.reset();await load();
        }catch(ex){msg(reason(ex));}finally{working(false);}
    }
    async function publish(){working(true);try{await api("/api/inventory/events/"+editing+"/publish","POST");edit("");await load();msg("Evento publicado no catálogo.");}
        catch(e){msg(reason(e));}finally{working(false);}}
    return <><h1>Meus eventos</h1><div className="grid">{rows.map(r=><article key={r.id}><h2>{r.nome}</h2><p>{r.status} · {r.vendidos} vendidos · {r.reservados} reservados</p>
    {r.status==="RASCUNHO"&&<button onClick={()=>edit(r.id)}>Gerenciar setores</button>}</article>)}</div>
    {editing?<><h2>Setores de {rows.find(x=>x.id===editing)?.nome}</h2><form onSubmit={e=>submit(e,"sector")}>
    <label>Nome do setor<input name="nome" required/></label><label>Preço em reais<input name="preco" type="number" min=".01" step=".01" required/></label>
    <label>Fileiras<input name="fileiras" type="number" min="1" max="50" required/></label><label>Assentos por fileira<input name="lugares" type="number" min="1" max="100" required/></label>
    <label><input type="checkbox" name="acessivel"/> Setor acessível</label><button disabled={busy}>Adicionar setor</button></form>
    <button disabled={busy} onClick={publish}>Publicar evento</button><button onClick={()=>edit("")}>Voltar</button></>:<><h2>Criar evento</h2>
    <form onSubmit={e=>submit(e,"event")}><label>Nome<input name="nome" required/></label><label>Local<input name="local" required/></label>
    <label>Data e hora<input type="datetime-local" name="quando" required/></label><label>Descrição<textarea name="descricao" required/></label>
    <button disabled={busy}>Criar rascunho</button></form></>}<p role="status">{feedback}</p></>;
}
