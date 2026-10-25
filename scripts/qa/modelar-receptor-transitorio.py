#!/usr/bin/env python3
"""Modelo de capacidad del receptor transitorio; números sintéticos, sin red ni archivos ciudadanos."""
import json
MIB=1024**2
GIB=1024**3

def simulate(monthly,size_mib,files=2,ack_and_restore_hours=24,retention_hours=24,days=365):
    pending=[];fraction=0.;peak=reserved=0
    for hour in range(days*24):
        # Mantenimiento horario: más conservador que el programador real de cinco minutos.
        pending=[(born,size,size+2*files*MIB) for born,size,_ in pending if hour-born<ack_and_restore_hours+retention_hours]
        fraction+=monthly/(30*24)
        while fraction>=1-1e-9:
            fraction-=1;size=size_mib*MIB
            peak=max(peak,sum(s+2*files*MIB for _,s,_ in pending)+2*size+2*files*MIB)
            reserved=max(reserved,sum(r for _,_,r in pending)+2*size+2*files*MIB)
            pending.append((hour,size,2*size+2*files*MIB))
    return dict(webMensuales=monthly,MiBIngreso=size_mib,archivos=files,horasEntregaRestauracion=ack_and_restore_hours,retencionHoras=retention_hours,picoBinariosGiB=round(peak/GIB,3),picoReservaGiB=round(reserved/GIB,3),cabeCuota6GiB=reserved<=6*GIB,originalesSinRetirarAnioGiB=round(monthly*days/30*size_mib/1024,3))

def main():
    rows=[simulate(n,size) for n,size in [(90,20),(300,20),(90,100),(300,100)]]
    rows.append(simulate(300,250,files=12))
    a=simulate(300,20);b=simulate(300,20,days=730)
    assert a['picoBinariosGiB']==b['picoBinariosGiB'] and a['picoReservaGiB']==b['picoReservaGiB']
    print(json.dumps(dict(escenarios=rows,indisponibilidadOchoDias=simulate(300,20,ack_and_restore_hours=192),presupuestoFijoGiB=dict(sistema=6,runtime=3,mysql=3,margenLibre=4),recibosMedidos=dict(comunicaciones=6,bytesReciboYACK=25650),nota='Meseta binaria a uno/dos años comprobada. Llegadas uniformes; no prueba rendimiento, ráfagas, antivirus ni capacidad del despliegue real.'),ensure_ascii=False,indent=2))
if __name__=='__main__':main()