#!/usr/bin/env python3
"""Modelo discreto de capacidad, no carga ni medición de rendimiento. Sin red ni escrituras."""
import json

MIB=1024**2
GIB=1024**3

def simulate(per_month, mib, retention_days=3, delivery_days=1, maintenance_days=1, days=365, files=2):
    pending=[];peak_physical=peak_reserved=0;acc=0.0
    for day in range(days):
        if day % maintenance_days == 0:
            pending=[(born,size,size+2*files*MIB) for born,size,_ in pending if day-born<delivery_days+retention_days]
        acc+=per_month/30
        while acc>=1-1e-9:
            acc-=1;size=mib*MIB
            # Pico de staging de un lote: bloques + original; 2 archivos y derivadas conservadoras (4 MiB).
            physical=sum(size+2*files*MIB for _,size,_ in pending)+2*size+2*files*MIB
            reserved=sum(reservation for _,_,reservation in pending)+2*size+2*files*MIB
            peak_physical=max(peak_physical,physical);peak_reserved=max(peak_reserved,reserved)
            pending.append((day,size,2*size+2*files*MIB))
        # El mantenimiento periódico compacta reservas del VALIDADO a bytes durables.
    return dict(ingresosMes=per_month,MiBPorIngreso=mib,dias=days,archivosPorIngreso=files,picoGiB=round(peak_physical/GIB,3),picoReservaGiB=round(peak_reserved/GIB,3),sinLiberacionGiB=round(per_month*mib*(days/30)/1024,3),cabeEnCuota6GiB=peak_reserved<=6*GIB)

def main():
    rows=[simulate(n,size) for n,size in [(90,20),(300,20),(90,100),(300,100)]]
    rows.append(simulate(300,250,files=12))
    # Una semana de indisponibilidad además del día normal de entrega.
    outage=simulate(300,20,delivery_days=8)
    print(json.dumps(dict(modelo='Supone entrega, restauración independiente válida y mantenimiento diario; no simula AV, RAM ni rendimiento.',escenarios=rows,atraso8Dias=outage),ensure_ascii=False,indent=2))
if __name__=='__main__':main()