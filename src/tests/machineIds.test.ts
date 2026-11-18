import * as fs from 'fs';
import * as path from 'path';
import { Repository, RecipeType } from '../repository';
import { GetMachine, machines } from '../machines';
import { machineRuleNames } from '../machineIds';
import { datasetDirectory } from '../dataset';
import { setupRepository } from './setup';

describe('Active dataset machine rules', () => {
    beforeAll(setupRepository);
    it('contains the complete active pair', () => {
        for (const file of ['data.bin', 'atlas.webp']) expect(fs.existsSync(path.join(datasetDirectory, file))).toBe(true);
    });
    it('resolves renamed machines by identity', () => {
        expect(GetMachine({id: 'i:gregtech:gt.blockmachines:32022'})).toBe(machines['Compact Fusion Computer MK-IV Prototype']);
        expect(GetMachine({id: 'i:unknown:machine:0'})).toBeUndefined();
        for (const rule of Object.values(machineRuleNames)) expect(machines[rule]).toBeDefined();
    });
    it('requires review when alternative machines gain or lose custom rules', () => {
        const generic = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/generic-machines.json'), 'utf8'));
        const actual: Record<string, string> = {};
        for (const p of Repository.current.recipeTypes) {
            for (const item of Repository.current.GetObject(p, RecipeType).multiblocks) {
                if (!GetMachine(item)) actual[item.id] = item.name;
            }
        }
        expect(actual).toEqual(generic);
    });
});
