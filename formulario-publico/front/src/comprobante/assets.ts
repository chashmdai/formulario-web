import logoSvg from '../prototipo/assets/logo.svg?raw';
import roboto400Latin from '@fontsource/roboto/files/roboto-latin-400-normal.woff?url';
import roboto400LatinExt from '@fontsource/roboto/files/roboto-latin-ext-400-normal.woff?url';
import roboto400Greek from '@fontsource/roboto/files/roboto-greek-400-normal.woff?url';
import roboto400Cyrillic from '@fontsource/roboto/files/roboto-cyrillic-400-normal.woff?url';
import roboto400Vietnamese from '@fontsource/roboto/files/roboto-vietnamese-400-normal.woff?url';
import roboto400Symbols from '@fontsource/roboto/files/roboto-symbols-400-normal.woff?url';
import roboto400Math from '@fontsource/roboto/files/roboto-math-400-normal.woff?url';
import roboto500Latin from '@fontsource/roboto/files/roboto-latin-500-normal.woff?url';
import roboto500LatinExt from '@fontsource/roboto/files/roboto-latin-ext-500-normal.woff?url';
import cabin600Latin from '@fontsource/cabin/files/cabin-latin-600-normal.woff?url';
import cabin600LatinExt from '@fontsource/cabin/files/cabin-latin-ext-600-normal.woff?url';
import cabin700Latin from '@fontsource/cabin/files/cabin-latin-700-normal.woff?url';
import cabin700LatinExt from '@fontsource/cabin/files/cabin-latin-ext-700-normal.woff?url';
import type {FontKey,PdfAssets} from './pdf';

/** Fuentes OFL y logotipo servidos desde el mismo origen (CSP `connect-src 'self'`); se cargan solo al generar el PDF. */
const urls:Record<FontKey,string>={
  'roboto-400-latin':roboto400Latin,'roboto-400-latin-ext':roboto400LatinExt,'roboto-400-greek':roboto400Greek,'roboto-400-cyrillic':roboto400Cyrillic,
  'roboto-400-vietnamese':roboto400Vietnamese,'roboto-400-symbols':roboto400Symbols,'roboto-400-math':roboto400Math,
  'roboto-500-latin':roboto500Latin,'roboto-500-latin-ext':roboto500LatinExt,'cabin-600-latin':cabin600Latin,'cabin-600-latin-ext':cabin600LatinExt,
  'cabin-700-latin':cabin700Latin,'cabin-700-latin-ext':cabin700LatinExt,
};
let cached:Promise<PdfAssets>|null=null;
export function loadPdfAssets():Promise<PdfAssets>{
  cached??=(async()=>{
    const entries=await Promise.all(Object.entries(urls).map(async([key,url])=>{
      const response=await fetch(url,{credentials:'same-origin'});if(!response.ok)throw new Error('Fuente no disponible');
      return [key,await response.arrayBuffer()] as const;
    }));
    return {fonts:Object.fromEntries(entries) as Record<FontKey,ArrayBuffer>,logoSvg};
  })();
  cached.catch(()=>{cached=null;});
  return cached;
}
