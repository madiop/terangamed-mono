import { Routes } from '@angular/router';
import { authGuard, roleGuard } from '@core/auth/auth.guard';

/**
 * Routes top-level — protégées par {@link authGuard} et {@link roleGuard}
 * pour les sections admin.
 */
export const routes: Routes = [
  // Pages publiques (pas de layout, pas de guard)
  {
    path: 'login',
    loadComponent: () =>
      import('@features/auth/login-page.component').then((m) => m.LoginPageComponent),
    title: 'Connexion — TerangaMed'
  },
  {
    path: 'unauthorized',
    loadComponent: () =>
      import('@features/auth/unauthorized-page.component').then(
        (m) => m.UnauthorizedPageComponent
      ),
    title: 'Accès refusé — TerangaMed'
  },

  // Application authentifiée (sous le MainLayout)
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('@core/layout/main-layout.component').then((m) => m.MainLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('@features/dashboard/dashboard-page.component').then(
            (m) => m.DashboardPageComponent
          ),
        title: 'Tableau de bord — TerangaMed'
      },

      // ─────── Module Medical (étape 9.6) ───────
      {
        // Dossier médical d'un patient — accessible depuis la fiche patient
        path: 'patients/:patientId/medical-record',
        canActivate: [roleGuard(['ADMIN', 'DOCTOR'])],
        loadComponent: () =>
          import('@features/medical/pages/medical-record-page.component').then(
            (m) => m.MedicalRecordPageComponent
          ),
        title: 'Dossier médical — TerangaMed'
      },
      {
        path: 'consultations',
        canActivate: [roleGuard(['ADMIN', 'DOCTOR'])],
        children: [
          {
            // 'new' AVANT ':id' (idem patient/appointment)
            path: 'new',
            loadComponent: () =>
              import('@features/medical/pages/consultation-form-page.component').then(
                (m) => m.ConsultationFormPageComponent
              ),
            title: 'Nouvelle consultation — TerangaMed'
          },
          {
            path: ':id/edit',
            loadComponent: () =>
              import('@features/medical/pages/consultation-form-page.component').then(
                (m) => m.ConsultationFormPageComponent
              ),
            title: 'Modifier consultation — TerangaMed'
          },
          {
            // Édition de l'ordonnance liée — segment statique 'prescription'
            // déclaré AVANT ':id' simple pour matcher en priorité.
            path: ':id/prescription',
            loadComponent: () =>
              import('@features/medical/pages/prescription-form-page.component').then(
                (m) => m.PrescriptionFormPageComponent
              ),
            title: 'Ordonnance — TerangaMed'
          },
          {
            path: ':id',
            loadComponent: () =>
              import('@features/medical/pages/consultation-detail-page.component').then(
                (m) => m.ConsultationDetailPageComponent
              ),
            title: 'Détail consultation — TerangaMed'
          }
        ]
      },

      // ─────── Module Patients (étape 9.4) ───────
      {
        path: 'patients',
        canActivate: [roleGuard(['ADMIN', 'DOCTOR', 'RECEPTIONIST'])],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('@features/patients/pages/patients-list-page.component').then(
                (m) => m.PatientsListPageComponent
              ),
            title: 'Patients — TerangaMed'
          },
          {
            // ATTENTION : 'new' DOIT être déclaré AVANT ':id' sinon Angular
            // matche d'abord :id et passe "new" comme paramètre numérique →
            // redirection vers la liste car NaN.
            path: 'new',
            loadComponent: () =>
              import('@features/patients/pages/patient-form-page.component').then(
                (m) => m.PatientFormPageComponent
              ),
            title: 'Nouveau patient — TerangaMed'
          },
          {
            path: ':id/edit',
            loadComponent: () =>
              import('@features/patients/pages/patient-form-page.component').then(
                (m) => m.PatientFormPageComponent
              ),
            title: 'Modifier patient — TerangaMed'
          },
          {
            // Détail patient — :id est numérique (validation côté composant)
            path: ':id',
            loadComponent: () =>
              import('@features/patients/pages/patient-detail-page.component').then(
                (m) => m.PatientDetailPageComponent
              ),
            title: 'Dossier patient — TerangaMed'
          }
        ]
      },

      // ─────── Module Rendez-vous (étape 9.5) ───────
      {
        path: 'appointments',
        canActivate: [roleGuard(['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT'])],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('@features/appointments/pages/appointments-list-page.component').then(
                (m) => m.AppointmentsListPageComponent
              ),
            title: 'Rendez-vous — TerangaMed'
          },
          {
            // 'new' AVANT ':id' — sinon Number("new") = NaN et redirection
            path: 'new',
            loadComponent: () =>
              import('@features/appointments/pages/appointment-form-page.component').then(
                (m) => m.AppointmentFormPageComponent
              ),
            title: 'Nouveau rendez-vous — TerangaMed'
          },
          {
            path: ':id/edit',
            loadComponent: () =>
              import('@features/appointments/pages/appointment-form-page.component').then(
                (m) => m.AppointmentFormPageComponent
              ),
            title: 'Modifier rendez-vous — TerangaMed'
          },
          {
            // Détail RDV — :id numérique validé côté composant
            path: ':id',
            loadComponent: () =>
              import('@features/appointments/pages/appointment-detail-page.component').then(
                (m) => m.AppointmentDetailPageComponent
              ),
            title: 'Détail rendez-vous — TerangaMed'
          }
        ]
      },

      // ─────── Section admin (étape 9.7) ───────
      {
        path: 'admin',
        canActivate: [roleGuard(['ADMIN'])],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'staff' },
          {
            // Section "Personnel médical" — gestion des médecins (étape 9.7)
            path: 'staff',
            children: [
              {
                path: '',
                loadComponent: () =>
                  import('@features/admin/pages/doctors-list-page.component').then(
                    (m) => m.DoctorsListPageComponent
                  ),
                title: 'Personnel — TerangaMed'
              },
              {
                // 'new' AVANT ':id' — sinon Number("new") = NaN et redirection
                path: 'new',
                loadComponent: () =>
                  import('@features/admin/pages/doctor-form-page.component').then(
                    (m) => m.DoctorFormPageComponent
                  ),
                title: 'Nouveau médecin — TerangaMed'
              },
              {
                path: ':id/edit',
                loadComponent: () =>
                  import('@features/admin/pages/doctor-form-page.component').then(
                    (m) => m.DoctorFormPageComponent
                  ),
                title: 'Modifier médecin — TerangaMed'
              },
              {
                // Détail médecin — :id numérique validé côté composant
                path: ':id',
                loadComponent: () =>
                  import('@features/admin/pages/doctor-detail-page.component').then(
                    (m) => m.DoctorDetailPageComponent
                  ),
                title: 'Médecin — TerangaMed'
              }
            ]
          }
        ]
      }

      // 9.5 → appointments, 9.6 → consultations
    ]
  },

  { path: '**', redirectTo: 'dashboard' }
];
