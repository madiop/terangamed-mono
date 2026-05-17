import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '@core/auth/auth.service';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let mockAuth: { accessToken: jest.Mock };

  beforeEach(() => {
    mockAuth = { accessToken: jest.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuth }
      ]
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it("ajoute le Bearer token aux requêtes /api/", () => {
    mockAuth.accessToken.mockReturnValue('abc.def.ghi');
    http.get('/api/patients').subscribe();
    const req = controller.expectOne('/api/patients');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    req.flush({});
  });

  it("ne touche pas les URLs non /api/ (ex: Keycloak discovery)", () => {
    mockAuth.accessToken.mockReturnValue('abc');
    http.get('https://keycloak.example/realms/x/.well-known/openid-configuration').subscribe();
    const req = controller.expectOne(
      'https://keycloak.example/realms/x/.well-known/openid-configuration'
    );
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it("skip si pas d'access token (laisse passer pour 401 propagé)", () => {
    mockAuth.accessToken.mockReturnValue(null);
    http.get('/api/patients').subscribe();
    const req = controller.expectOne('/api/patients');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it("respecte un Authorization existant (custom)", () => {
    mockAuth.accessToken.mockReturnValue('should-not-be-used');
    http
      .get('/api/x', { headers: { Authorization: 'Basic abc' } })
      .subscribe();
    const req = controller.expectOne('/api/x');
    expect(req.request.headers.get('Authorization')).toBe('Basic abc');
    req.flush({});
  });
});
