import { Repository } from "../repository";
import { PageModel, RecipeGroupModel, RecipeModel, serializer } from '../page';
import { SolvePage } from '../solver';
import * as fs from 'fs';
import * as path from 'path';
import { setupRepository } from './setup';

function loadTestFiles(): string[] {
    const testDir = path.join(__dirname, '..', '..', 'tests');
    const files = fs.readdirSync(testDir);
    return files.filter(file => file.endsWith('.gtnh'));
}

const testFiles = loadTestFiles();

function getRecipeExpectations(recipe: RecipeModel) {
    return {
        recipesPerMinute: recipe.recipesPerMinute,
        crafterCount: recipe.crafterCount,
        powerFactor: recipe.powerFactor,
        overclockFactor: recipe.overclockFactor,
        overclockName: recipe.overclockName,
        id: recipe.recipeId
    };
}

function processRecipeGroup(group: RecipeGroupModel): any[] {
    const expectations: any[] = [];
    for (const element of group.elements) {
        if (element instanceof RecipeModel) {
            expectations.push(getRecipeExpectations(element));
        } else if (element instanceof RecipeGroupModel) {
            expectations.push(...processRecipeGroup(element));
        }
    }
    return expectations;
}

describe('Solver', () => {
    beforeAll(async () => {
        await setupRepository();
    });

    describe('Test Files', () => {
        it('should have test files', () => {
            expect(testFiles.length).toBeGreaterThan(0);
        });
    });

    it('defaults older pages to average power and round-trips the selected mode', () => {
        const page = new PageModel({ settings: { wholeRecipes: true } });
        expect(page.settings.showMaxPower).toBe(false);
        page.settings.showMaxPower = true;
        const saved = JSON.parse(JSON.stringify(serializer.Serialize(page)));
        const reloaded = new PageModel(saved);
        expect(reloaded.settings.showMaxPower).toBe(true);
        expect(reloaded.settings.wholeRecipes).toBe(true);
    });

    testFiles.forEach((testFile: string) => {
        describe(`Processing ${testFile}`, () => {
            it('should process without errors', async () => {
                const filePath = path.join(__dirname, '..', '..', 'tests', testFile);
                const fileContent = fs.readFileSync(filePath, 'utf-8');
                const pageData = JSON.parse(fileContent);
                
                function validate(group: any): void {
                    for (const entry of group.elements || []) {
                        if (entry.type === 'recipe') {
                            expect(Repository.current.GetById(entry.recipeId)).not.toBeNull();
                            if (entry.crafter) expect(Repository.current.GetById(entry.crafter)).not.toBeNull();
                        } else validate(entry);
                    }
                }
                validate(pageData.rootGroup);
                for (const product of pageData.products || []) expect(Repository.current.GetById(product.goodsId)).not.toBeNull();
                const page = new PageModel(pageData);
                SolvePage(page);
                expect(page.status).toBe("solved");

                // Get expectations for all recipes and create a snapshot
                const expectations = processRecipeGroup(page.rootGroup);
                expect(expectations).toMatchSnapshot();
            });
        });
    });
});
