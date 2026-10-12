const METHODS=new Set(["GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS"]);
const cors=r=>({"Access-Control-Allow-Origin":r.headers.get("Origin")||"*","Access-Control-Allow-Methods":"GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS","Access-Control-Allow-Headers":r.headers.get("Access-Control-Request-Headers")||"Content-Type, Authorization, Range, User-Agent, Referer, Origin, Cookie","Access-Control-Expose-Headers":"Content-Type, Content-Length, Content-Range, Accept-Ranges, Location, ETag, Last-Modified","Access-Control-Max-Age":"86400","Vary":"Origin"});
function out(r,s,b){return new Response(JSON.stringify(b),{status:s,headers:{"content-type":"application/json; charset=UTF-8",...cors(r)}})}
function valid(v){try{const u=new URL(v);return u.protocol==="http:"||u.protocol==="https:"}catch{return false}}
function headers(r){const h=new Headers();for(const n of ["accept","accept-language","authorization","cache-control","content-type","cookie","if-modified-since","if-none-match","origin","pragma","range","referer","user-agent"]){const v=r.headers.get(n);if(v)h.set(n,v)}return h}
export default{async fetch(request){
 if(request.method==="OPTIONS")return new Response(null,{status:204,headers:cors(request)});
 if(!METHODS.has(request.method))return out(request,405,{ok:false,error:"Method not allowed"});
 const target=new URL(request.url).searchParams.get("url");
 if(!target)return out(request,400,{ok:false,error:"Missing ?url=",service:"LazyTV PRO Proxy"});
 if(!valid(target))return out(request,400,{ok:false,error:"Invalid target URL"});
 const init={method:request.method,headers:headers(request),redirect:"follow"};
 if(!["GET","HEAD"].includes(request.method))init.body=request.body;
 try{
  const u=await fetch(target,init),h=new Headers(u.headers);
  for(const [k,v] of Object.entries(cors(request)))h.set(k,v);
  h.delete("set-cookie");
  return new Response(u.body,{status:u.status,statusText:u.statusText,headers:h});
 }catch{return out(request,502,{ok:false,error:"Upstream request failed"})}
}};
