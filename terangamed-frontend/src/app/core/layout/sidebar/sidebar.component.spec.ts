import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { SidebarComponent } from './sidebar.component';
import { AuthService } from '@core/auth/auth.service';
import { TerangaMedRole } from '@core/auth/auth.types';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let component: SidebarComponent;
  let logoutSpy: jest.Mock;

  function setupWith(roles: readonly TerangaMedRole[]) {
    logoutSpy = jest.fn();
    const mockAuth = {
      hasAnyRole: jest.fn(
        (required: readonly TerangaMedRole[]) =>
          required.length === 0 || required.some((r) => roles.includes(r))
      ),
      isAuthenticated: signal(true),
      currentUser: signal(null),
      roles: signal(roles),
      logout: logoutSpy
    };

    TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: mockAuth }]
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it("utilisateur DOCTOR ne voit pas l'item Personnel (ADMIN-only)", () => {
    setupWith(['DOCTOR']);
    const labels = component.visibleItems().map((i) => i.label);
    expect(labels).toContain('Tableau de bord');
    expect(labels).toContain('Patients');
    expect(labels).toContain('Consultations');
    expect(labels).not.toContain('Personnel');
  });

  it('utilisateur ADMIN voit Personnel + tous les autres', () => {
    setupWith(['ADMIN']);
    const labels = component.visibleItems().map((i) => i.label);
    expect(labels).toContain('Personnel');
    expect(labels).toContain('Patients');
    expect(labels).toContain('Facturation');
  });

  it("utilisateur PATIENT n'a accès qu'à Dashboard, RDV, Consultations, Facturation", () => {
    setupWith(['PATIENT']);
    const labels = component.visibleItems().map((i) => i.label);
    expect(labels).toEqual(
      expect.arrayContaining(['Tableau de bord', 'Rendez-vous', 'Consultations', 'Facturation'])
    );
    expect(labels).not.toContain('Patients');
    expect(labels).not.toContain('Personnel');
    expect(labels).not.toContain('Documents');
  });

  it('logout délègue à AuthService', () => {
    setupWith(['DOCTOR']);
    component.logout();
    expect(logoutSpy).toHaveBeenCalled();
  });
});
