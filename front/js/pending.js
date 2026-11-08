// Keep a form submit button stable across slow responses and recoverable errors.
export async function pendingForm(form, action){
 const button=form.querySelector('button[type="submit"],button:not([type])');
 if(button.disabled)return;
 const label=button.textContent;button.disabled=true;button.textContent='처리 중…';form.setAttribute('aria-busy','true');
 try{return await action();}
 finally{button.disabled=false;button.textContent=label;form.setAttribute('aria-busy','false');}
}
