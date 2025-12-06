# **README.md - Mercado Saludable**

## Descripción del Proyecto

Mercado Saludable es una aplicación móvil que genera planes de alimentación semanales personalizados, considerando:
- Condiciones médicas (diabetes, hipertensión, obesidad)
- Alergias alimentarias
- Presupuesto mensual del usuario
- Objetivos nutricionales individuales

La aplicación combina **Firebase** para autenticación y sincronización en la nube, con **Room** para almacenamiento local, ofreciendo una experiencia offline/online fluida.

## Características Principales

### Perfil de Salud Personalizado
- Registro con datos antropométricos (peso, altura, edad)
- Cálculo automático de IMC y clasificación
- Gestión de condiciones médicas y alergias
- Definición de presupuesto mensual

### Catálogo Inteligente de Recetas
- +40 recetas categorizadas (Desayuno, Almuerzo, Cena)
- Filtrado por condición médica y alergias
- Información nutricional detallada
- Búsqueda por ingredientes o nombre

### Generador de Menús Semanales
- Planificación automática de 7 días
- Distribución balanceada de comidas
- Cálculo de costo total y promedio diario
- Compatibilidad con restricciones alimentarias

### Sincronización en la Nube
- Autenticación con Firebase Auth
- Sincronización bidireccional Firebase ↔ Room
- Acceso offline a recetas y menús
- Backup automático de datos de usuario

## Tecnologías Utilizadas

### Backend & Persistencia
- **Firebase Authentication** - Autenticación de usuarios
- **Firebase Firestore** - Base de datos en la nube
- **Room Database** - Base de datos local SQLite
- **Coroutines** - Programación asíncrona

### Arquitectura & Patrones
- **MVVM (Model-View-ViewModel)** - Arquitectura principal
- **Repository Pattern** - Gestión unificada de datos
- **LiveData** - Observables reactivos
- **ViewBinding** - Binding seguro de vistas

### UI/UX
- **Material Design 3** - Design system moderno
- **Navigation Component** - Navegación entre fragments
- **RecyclerView** - Listas eficientes
- **ViewPager2** - Navegación por pestañas

## Requisitos del Sistema

- **Android Studio** - Flamingo o superior
- **Android SDK** - API 34 (Android 14)
- **Kotlin** - 1.9.0 o superior
- **Dispositivo** - Android 8.0 (API 26) o superior
- **Conexión a Internet** - Para sincronización inicial

## Instalación y Configuración

### 1. Clonar el Repositorio
```bash
https://github.com/nicolle-lo-ve/FINAL_PPDM.git
cd mercado-saludable
```

### 2. Configurar Firebase
1. Ve a [Firebase Console](https://console.firebase.google.com/)
2. Crea un nuevo proyecto "Mercado Saludable"
3. Agrega una app Android con el package name: `com.lasalle.mercadosaludable`
4. Descarga el archivo `google-services.json`
5. Colócalo en `app/google-services.json`

### 3. Configurar el Proyecto
1. Abre el proyecto en Android Studio
2. Espera a que se sincronicen las dependencias
3. Ejecuta la app en un emulador o dispositivo físico

### 4. Estructura del Proyecto
```
app/
├── src/main/
│   ├── java/com/lasalle/mercadosaludable/
│   │   ├── data/              # Modelos y repositorios
│   │   │   ├── local/         # Room Database y DAOs
│   │   │   ├── model/         # Entidades (User, Recipe, MenuPlan)
│   │   │   └── repository/    # AppRepository
│   │   ├── ui/                # Capa de presentación
│   │   │   ├── activity/      # Activities (Login, Register, Main)
│   │   │   ├── fragment/      # Fragments (Home, Recipes, etc.)
│   │   │   ├── viewmodel/     # ViewModels
│   │   │   └── adapter/       # Adapters para RecyclerView
│   │   └── di/               # Inyección de dependencias (futuro)
│   ├── res/                  # Recursos (layouts, strings, drawables)
│   └── AndroidManifest.xml
```

## 📸 Capturas de Pantalla

| Pantalla de Inicio | Registro | Perfil | Catálogo de Recetas | Detalle Receta | Menú Semanal |
|--------------------|----------|--------|---------------------|----------------|--------------|
| <img width="135" height="292" alt="image" src="https://github.com/user-attachments/assets/72136e81-76e6-4143-8b32-987589e7ac14" /> | <img width="135" height="292" alt="image" src="https://github.com/user-attachments/assets/f5683b5b-af94-4b00-9bd9-ec8d3f6f980b" /> | <img width="135" height="292" alt="image" src="https://github.com/user-attachments/assets/d2c6db25-d64d-4103-8b03-aee9428ac6c6" /> | <img width="135" height="292" alt="image" src="https://github.com/user-attachments/assets/8c36ea07-09bc-48e5-8bba-b56eeeedc0f0" /> | <img width="135" height="292" alt="image" src="https://github.com/user-attachments/assets/7085f8fd-be44-43af-b2f9-7e87cebf4ddb" /> | <img width="135" height="292" alt="image" src="https://github.com/user-attachments/assets/114c19ce-3dfd-4aed-b6de-c74051d7b896" /> |

## Estructura de la Base de Datos

### Firebase Firestore
```
/users/{userId}
    - name, email, age, weight, height
    - medicalConditions, allergies
    - nutritionalGoal, monthlyBudget

/recipes/{recipeId}
    - name, description, category
    - calories, protein, carbs, fats
    - suitableFor, ingredients, instructions
    - preparationTime, difficulty, estimatedCost

/menu_plans/{userId_planId}
    - userId, name, startDate, endDate
    - monday, tuesday, ..., sunday (IDs de recetas)
    - totalCalories, totalCost, isActive
```

### Room Database
- **users** - Perfiles locales de usuarios
- **recipes** - Cache local de recetas
- **menu_plans** - Planes de menú generados

## Arquitectura MVVM

<img width="1568" height="926" alt="image" src="https://github.com/user-attachments/assets/64bbd3e3-6201-4ec6-be4c-38def1ce6645" />


## Funcionalidades Implementadas

### Completadas
1. **Autenticación y Registro**
   - Registro con validación de campos
   - Login con Firebase Auth
   - Persistencia de sesión

2. **Gestión de Perfil de Salud**
   - Formulario completo de datos de salud
   - Cálculo automático de IMC
   - Gestión de condiciones y alergias

3. **Catálogo de Recetas**
   - 40+ recetas predefinidas
   - Filtrado por categoría y condición
   - Búsqueda por texto
   - Vista detallada con información nutricional

4. **Generador de Menús**
   - Generación automática semanal
   - Consideración de restricciones
   - Cálculo de costos y calorías
   - Visualización por días

5. **Sincronización**
   - Sincronización bidireccional
   - Funcionamiento offline
   - Backup en la nube

###  Próximas Mejoras
- Compartir menús en redes sociales
- Lista de compras automática
- Seguimiento de progreso de salud
- Notificaciones de recordatorio de comidas
- Generacion de Menu Semanal con IA
- Integración con API de supermercados

##  Pruebas

### Pruebas Manuales
1. **Flujo de Registro**
   - Validar todos los campos requeridos
   - Verificar cálculo de IMC
   - Confirmar sincronización con Firebase

2. **Generación de Menú**
   - Probar con diferentes combinaciones de alergias
   - Verificar que no se repitan recetas innecesariamente
   - Confirmar cálculo de presupuesto

3. **Sincronización**
   - Probar modo avión y recuperación
   - Verificar consistencia de datos






